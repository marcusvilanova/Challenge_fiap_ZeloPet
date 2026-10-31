package br.com.zelo.service;

import br.com.zelo.ai.KnowledgeBase;
import br.com.zelo.ai.NextAction;
import br.com.zelo.ai.Species;
import br.com.zelo.ai.SymptomMention;
import br.com.zelo.ai.TriageAnalysis;
import br.com.zelo.ai.TriageEngine;
import br.com.zelo.api.dto.TriageResult;
import br.com.zelo.api.dto.TriageResult.Engine;
import br.com.zelo.api.dto.TriageResult.SymptomDetail;
import br.com.zelo.domain.ChatSession;
import br.com.zelo.domain.Pet;
import br.com.zelo.domain.Triage;
import br.com.zelo.repo.TriageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Persiste a triagem junto da resposta estruturada (rastreabilidade) e a registra na linha do tempo. */
@Service
public class TriageService {

    private static final int MAX_REASONS = 2;
    private static final double NEEDS_MORE_INFO_BELOW = 0.50;
    private static final String PLACEHOLDER_JSON = "{}";

    private final TriageRepository triages;
    private final TimelineService timeline;
    private final KnowledgeBase knowledge;
    private final ObjectMapper mapper;
    private final Clock clock;

    public TriageService(TriageRepository triages, TimelineService timeline, KnowledgeBase knowledge,
                         ObjectMapper mapper, Clock clock) {
        this.triages = triages;
        this.timeline = timeline;
        this.knowledge = knowledge;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * @param source   "chat" ou "api"
     * @param llmLabel modelo generativo efetivamente usado nesta triagem, ou "local-fallback"
     */
    @Transactional
    public TriageResult record(Pet pet, ChatSession session, String source, String inputText, TriageAnalysis analysis,
                               String llmLabel) {
        LocalDateTime now = LocalDateTime.now(clock);
        TriageResult draft = build(pet, analysis, llmLabel, now);
        Triage saved = triages.saveAndFlush(new Triage(pet, session, source, inputText, PLACEHOLDER_JSON,
                draft.priority(), draft.nextAction(), draft.confidence(), draft.modelVersion(),
                draft.humanReviewRequired(), now));
        TriageResult result = draft.withTriageId(saved.getPublicId());
        saved.replaceResultJson(write(result));
        timeline.record(pet, "triage", "Triagem preliminar: prioridade " + result.priority().wire(),
                summary(result), source, saved.getId(), null);
        return result;
    }

    @Transactional(readOnly = true)
    public TriageResult toResult(Triage triage) {
        try {
            return mapper.readValue(triage.getResultJson(), TriageResult.class).withShared(triage.isSharedWithClinic());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Triagem " + triage.getPublicId() + " com JSON inválido", e);
        }
    }

    @Transactional(readOnly = true)
    public Triage requireOwned(Long userId, String publicId) {
        return triages.findByIdAndPetOwnerId(parse(publicId), userId)
                .orElseThrow(() -> new NotFoundException("Triagem não encontrada"));
    }

    /** Estado ATUAL de compartilhamento (por id interno): o histórico do chat guarda só o instantâneo de quando a triagem foi feita. */
    @Transactional(readOnly = true)
    public Map<Long, Boolean> sharedStates(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return triages.findAllById(ids).stream().collect(Collectors.toMap(Triage::getId, Triage::isSharedWithClinic));
    }

    /** Revogar o consentimento desfaz o compartilhamento: um novo consentimento NÃO reexibe triagens antigas. */
    @Transactional
    public void unshareAllOf(Long petId) {
        triages.unshareAllOf(petId);
    }

    @Transactional
    public Triage markShared(Long triageId) {
        Triage triage = triages.findById(triageId).orElseThrow(() -> new NotFoundException("Triagem não encontrada"));
        triage.markShared();
        return triage;
    }

    private TriageResult build(Pet pet, TriageAnalysis a, String llmLabel, LocalDateTime now) {
        List<SymptomDetail> details = a.active().stream().map(m -> detail(m, pet.getSpecies())).toList();
        NextAction action = NextAction.forPriority(a.priority());
        String reason = a.reasons().isEmpty()
                ? "Nenhum sinal de alerta identificado no relato"
                : a.reasons().stream().limit(MAX_REASONS).collect(Collectors.joining("; "));
        boolean needsMoreInfo = a.unrecognized() || a.vague() || a.confidence() < NEEDS_MORE_INFO_BELOW;
        return new TriageResult(null, pet.getPublicId(), details.stream().map(SymptomDetail::id).toList(), details,
                a.priority(), action, action.label(), reason, a.reasons(), a.redFlagIds(), a.contextFactors(),
                a.confidence(), TriageResult.MODEL_VERSION,
                new Engine(TriageEngine.RULES_VERSION, knowledge.version(), llmLabel), a.humanReviewRequired(),
                TriageResult.DISCLAIMER, a.durationHours(), needsMoreInfo, now.atOffset(ZoneOffset.UTC), false);
    }

    private SymptomDetail detail(SymptomMention mention, Species species) {
        double frequency = knowledge.symptom(mention.id()).referenceFrequency(species);
        Double rounded = frequency > 0 ? Math.round(frequency * 100.0) / 100.0 : null;
        return new SymptomDetail(mention.id(), mention.label(), mention.category(), mention.evidence(), rounded);
    }

    private static long parse(String publicId) {
        try {
            return Triage.parsePublicId(publicId);
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("Triagem não encontrada");
        }
    }

    private String write(TriageResult result) {
        try {
            return mapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar a triagem", e);
        }
    }

    private static String summary(TriageResult result) {
        String reasons = result.reasons().stream().limit(MAX_REASONS).collect(Collectors.joining("; "));
        String symptoms = result.symptomDetails().stream().map(SymptomDetail::label).collect(Collectors.joining(", "));
        if (symptoms.isEmpty()) {
            return reasons;
        }
        return reasons.isEmpty() ? symptoms : symptoms + " — " + reasons;
    }
}
