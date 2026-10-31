package br.com.zelo.service;

import br.com.zelo.ai.KnowledgeBase;
import br.com.zelo.ai.PetContext;
import br.com.zelo.ai.Priority;
import br.com.zelo.ai.SymptomMention;
import br.com.zelo.ai.TextNormalizer;
import br.com.zelo.ai.TriageAnalysis;
import br.com.zelo.api.dto.ApiDtos.ChatHistory;
import br.com.zelo.api.dto.ApiDtos.ChatMessageResponse;
import br.com.zelo.api.dto.ApiDtos.ChatReply;
import br.com.zelo.api.dto.ApiDtos.ChatRequest;
import br.com.zelo.api.dto.ApiDtos.TriageRequest;
import br.com.zelo.api.dto.TriageResult;
import br.com.zelo.api.dto.VisionResult;
import br.com.zelo.chat.BotTurn;
import br.com.zelo.chat.BotTurn.ShareAction;
import br.com.zelo.chat.ConversationFlow;
import br.com.zelo.chat.ConversationFlow.CareContext;
import br.com.zelo.chat.ConversationFlow.SymptomAssist;
import br.com.zelo.chat.ConversationState;
import br.com.zelo.chat.ReplyComposer;
import br.com.zelo.domain.AppUser;
import br.com.zelo.domain.ChatMessage;
import br.com.zelo.domain.ChatMessage.Author;
import br.com.zelo.domain.ChatSession;
import br.com.zelo.domain.Consent;
import br.com.zelo.domain.Pet;
import br.com.zelo.domain.Triage;
import br.com.zelo.llm.LlmClient;
import br.com.zelo.llm.NoopLlmClient;
import br.com.zelo.llm.ResponseGuard;
import br.com.zelo.repo.AppUserRepository;
import br.com.zelo.repo.ChatMessageRepository;
import br.com.zelo.repo.ChatSessionRepository;
import br.com.zelo.security.AppUserDetails;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Orquestra uma mensagem do tutor: máquina de estados da conversa (determinística) → triagem persistida →
 * LLM opcional (extração de sintomas e reescrita, sempre validadas) → consentimento e compartilhamento.
 */
@Service
public class ChatService {

    private static final Logger LOG = LoggerFactory.getLogger(ChatService.class);
    private static final int HISTORY_LIMIT = 60;
    private static final String AI_EVIDENCE = "(identificado pela IA)";
    private static final String MEDICATION_REFUSAL_MARK = "Não posso indicar remédios";

    private final PetService pets;
    private final CarePlanService carePlan;
    private final TriageService triages;
    private final ConsentService consents;
    private final ConversationFlow flow;
    private final LlmClient llm;
    private final KnowledgeBase knowledge;
    private final ChatSessionRepository sessions;
    private final ChatMessageRepository messages;
    private final AppUserRepository users;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final ReplyComposer composer = new ReplyComposer();

    public ChatService(PetService pets, CarePlanService carePlan, TriageService triages, ConsentService consents,
                       ConversationFlow flow, LlmClient llm, KnowledgeBase knowledge, ChatSessionRepository sessions,
                       ChatMessageRepository messages, AppUserRepository users, ObjectMapper mapper, Clock clock) {
        this.pets = pets;
        this.carePlan = carePlan;
        this.triages = triages;
        this.consents = consents;
        this.flow = flow;
        this.llm = llm;
        this.knowledge = knowledge;
        this.sessions = sessions;
        this.messages = messages;
        this.users = users;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** Abre a conversa do pet: retoma a mais recente ou cria uma nova com a saudação personalizada. */
    @Transactional
    public ChatHistory open(AppUserDetails me, String petId, boolean fresh) {
        Pet pet = pets.requireOwned(me.id(), petId);
        Optional<ChatSession> existing = fresh ? Optional.empty()
                : sessions.findFirstByPetIdAndUserIdOrderByUpdatedAtDesc(pet.getId(), me.id());
        ChatSession session = existing.orElseGet(() -> createWithGreeting(me, pet));
        return history(session);
    }

    @Transactional
    public ChatReply handle(AppUserDetails me, ChatRequest request) {
        Pet pet = pets.requireOwned(me.id(), request.petId());
        ChatSession session = request.sessionId() == null
                ? createWithGreeting(me, pet)
                : sessions.findByIdAndUserId(request.sessionId(), me.id())
                        .filter(s -> s.getPet().getId().equals(pet.getId()))
                        .orElseThrow(() -> new NotFoundException("Conversa não encontrada"));
        String text = request.text().trim();
        LocalDateTime now = LocalDateTime.now(clock);
        messages.save(new ChatMessage(session, Author.USER, text, null, now));

        PetContext context = pets.contextFor(pet);
        CareContext care = carePlan.contextFor(pet);
        AtomicBoolean llmContributed = new AtomicBoolean(false);
        SymptomAssist assist = llm.enabled() ? (t, current) -> llmSymptoms(t, context, current, llmContributed) : null;

        BotTurn turn = flow.next(readState(session), text, context, care, assist);
        Polished polished = polish(turn, text, context, llmContributed);

        TriageResult triage = null;
        Long triageId = turn.nextState().lastTriageId();
        if (turn.triage() != null) {
            triage = triages.record(pet, session, "chat", turn.reportText() == null ? text : turn.reportText(),
                    turn.triage(), polished.llmUsed() ? llm.label() : NoopLlmClient.LABEL);
            triageId = Triage.parsePublicId(triage.triageId());
        }
        String sharedTriageId = applyShare(me, pet, turn.share(), triageId);
        if (triage != null && sharedTriageId != null && sharedTriageId.equals(triage.triageId())) {
            triage = triage.withShared(true);
        }

        session.updateState(write(turn.nextState().withLastTriage(triageId)), now);
        ChatMessage botMessage = messages.save(new ChatMessage(session, Author.BOT, polished.text(),
                write(new BotMeta(turn.kind().name(), turn.quickReplies(), triage, null, polished.status())), LocalDateTime.now(clock)));
        return new ChatReply(session.getId(), toResponse(botMessage, turn.quickReplies(), triage, turn.kind().name(),
                polished.status()), turn.nextState().stage().name(), sharedTriageId);
    }

    /** Triagem avulsa (contrato POST /api/triage): sem conversa, devolve a saída estruturada e a persiste. */
    @Transactional
    public TriageResult triageOnce(AppUserDetails me, TriageRequest request) {
        Pet pet = pets.requireOwned(me.id(), request.petId());
        PetContext context = pets.contextFor(pet);
        AtomicBoolean llmContributed = new AtomicBoolean(false);
        SymptomAssist assist = llm.enabled() ? (t, current) -> llmSymptoms(t, context, current, llmContributed) : null;
        TriageAnalysis analysis = flow.analyzeStateless(request.text().trim(), context, assist);
        return triages.record(pet, null, "api", request.text().trim(), analysis,
                llmContributed.get() ? llm.label() : NoopLlmClient.LABEL);
    }

    @Transactional(readOnly = true)
    public ChatHistory history(AppUserDetails me, Long sessionId) {
        ChatSession session = sessions.findByIdAndUserId(sessionId, me.id())
                .orElseThrow(() -> new NotFoundException("Conversa não encontrada"));
        return history(session);
    }

    // ------------------------------------------------------------------------------ internos
    private ChatSession createWithGreeting(AppUserDetails me, Pet pet) {
        AppUser user = users.findById(me.id()).orElseThrow(() -> new NotFoundException("Usuário não encontrado"));
        LocalDateTime now = LocalDateTime.now(clock);
        ChatSession session = sessions.save(new ChatSession(pet, user, write(ConversationState.initial()), now));
        PetContext context = pets.contextFor(pet);
        List<String> quick = List.of("Relatar um sintoma", "Próximos cuidados", "O que você faz?");
        messages.save(new ChatMessage(session, Author.BOT, composer.greeting(context, carePlan.contextFor(pet).highlight()),
                write(new BotMeta("SMALLTALK", quick, null, null, "not_used")), now));
        return session;
    }

    private ChatHistory history(ChatSession session) {
        List<ChatMessage> latest = new ArrayList<>(
                messages.findBySessionIdOrderByIdDesc(session.getId(), PageRequest.of(0, HISTORY_LIMIT)));
        Collections.reverse(latest);
        Map<Long, Boolean> shared = triages.sharedStates(latest.stream()
                .filter(m -> m.getAuthor() == Author.BOT)
                .map(m -> readMeta(m.getMetaJson()).triage())
                .filter(Objects::nonNull)
                .map(t -> Triage.parsePublicId(t.triageId()))
                .collect(Collectors.toSet()));
        return new ChatHistory(session.getId(), latest.stream().map(m -> fromStored(m, shared)).toList());
    }

    private ChatMessageResponse fromStored(ChatMessage message, Map<Long, Boolean> sharedNow) {
        if (message.getAuthor() == Author.USER) {
            return new ChatMessageResponse(message.getId(), "USER", message.getContent(), List.of(), null, null, null, null,
                    message.getCreatedAt().atOffset(ZoneOffset.UTC));
        }
        BotMeta meta = readMeta(message.getMetaJson());
        TriageResult triage = meta.triage() == null ? null : meta.triage().withShared(
                sharedNow.getOrDefault(Triage.parsePublicId(meta.triage().triageId()), meta.triage().sharedWithClinic()));
        return new ChatMessageResponse(message.getId(), "BOT", message.getContent(), meta.quickReplies(), triage,
                meta.vision(), meta.kind(), meta.llmStatus(), message.getCreatedAt().atOffset(ZoneOffset.UTC));
    }

    private ChatMessageResponse toResponse(ChatMessage message, List<String> quick, TriageResult triage, String kind,
                                           String llmStatus) {
        return new ChatMessageResponse(message.getId(), "BOT", message.getContent(), quick, triage, null, kind, llmStatus,
                message.getCreatedAt().atOffset(ZoneOffset.UTC));
    }

    private record Polished(String text, String status, boolean llmUsed) {
    }

    /** Reescreve só triagens (texto com rascunho seguro); qualquer falha ou reprovação mantém o rascunho. */
    private Polished polish(BotTurn turn, String userText, PetContext context, AtomicBoolean extractionUsed) {
        if (!llm.enabled()) {
            return new Polished(turn.text(), "disabled", false);
        }
        if (!turn.draft() || turn.triage() == null) {
            return new Polished(turn.text(), extractionUsed.get() ? "extraction_only" : "not_used", extractionUsed.get());
        }
        if (turn.triage().priority().level() >= Priority.ALTA.level()) {
            // alta/emergência: o texto determinístico é mantido sem reescrita (menos superfície de risco)
            return new Polished(turn.text(), "deterministic_high_priority", extractionUsed.get());
        }
        Optional<String> rewritten = llm.rewriteReply(userText, context, turn.triage(), turn.text());
        if (rewritten.isEmpty()) {
            return new Polished(turn.text(), "error_fallback", extractionUsed.get());
        }
        String candidate = ResponseGuard.sanitize(rewritten.get());
        ResponseGuard.Verdict verdict = ResponseGuard.check(candidate, turn.triage().priority(), turn.text());
        boolean keepsRefusal = !turn.text().contains(MEDICATION_REFUSAL_MARK)
                || TextNormalizer.normalize(candidate).matches(".*(remedio|medicament).*");
        if (!verdict.accepted() || !keepsRefusal) {
            LOG.info("Resposta do LLM reprovada pelo guard: {}", verdict.accepted() ? "perdeu recusa de medicamento" : verdict.reason());
            return new Polished(turn.text(), "rejected_fallback", extractionUsed.get());
        }
        return new Polished(candidate, "used", true);
    }

    private List<SymptomMention> llmSymptoms(String text, PetContext context, TriageAnalysis current, AtomicBoolean used) {
        Map<String, String> allowed = new LinkedHashMap<>();
        knowledge.symptoms().forEach(s -> allowed.put(s.id(), s.label()));
        List<String> known = current.active().stream().map(SymptomMention::id).toList();
        List<SymptomMention> extra = llm.extractSymptoms(text, context, allowed).stream()
                .filter(id -> !known.contains(id) && knowledge.knows(id))
                .map(id -> {
                    var def = knowledge.symptom(id);
                    return new SymptomMention(def.id(), def.label(), def.category(), def.severityFor(context.species()),
                            AI_EVIDENCE, false, 0, 0);
                }).toList();
        if (!extra.isEmpty()) {
            used.set(true);
        }
        return extra;
    }

    private String applyShare(AppUserDetails me, Pet pet, ShareAction action, Long triageId) {
        if (action != ShareAction.GRANT || triageId == null) {
            return null;
        }
        consents.grant(me.id(), pet, Consent.SCOPE_TRIAGE_AND_CLINIC);
        consents.shareTriage(triageId);
        return Triage.publicId(triageId);
    }

    private ConversationState readState(ChatSession session) {
        try {
            return mapper.readValue(session.getStateJson(), ConversationState.class);
        } catch (JsonProcessingException e) {
            LOG.warn("Estado da conversa {} ilegível; reiniciando", session.getId());
            return ConversationState.initial();
        }
    }

    private BotMeta readMeta(String json) {
        try {
            return json == null ? new BotMeta("SMALLTALK", List.of(), null, null, null) : mapper.readValue(json, BotMeta.class);
        } catch (JsonProcessingException e) {
            return new BotMeta("SMALLTALK", List.of(), null, null, null);
        }
    }

    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar o estado da conversa", e);
        }
    }

    /** Metadados da mensagem do bot persistidos junto do texto. */
    record BotMeta(String kind, List<String> quickReplies, TriageResult triage, VisionResult vision, String llmStatus) {
    }
}
