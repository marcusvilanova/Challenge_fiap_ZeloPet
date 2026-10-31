package br.com.zelo.vision;

import br.com.zelo.ai.NextAction;
import br.com.zelo.ai.PetContext;
import br.com.zelo.ai.Priority;
import br.com.zelo.ai.Species;
import br.com.zelo.api.dto.VisionResult;
import br.com.zelo.api.dto.VisionResult.Prediction;
import br.com.zelo.domain.ChatMessage;
import br.com.zelo.domain.ChatMessage.Author;
import br.com.zelo.domain.ChatSession;
import br.com.zelo.domain.ImageAnalysis;
import br.com.zelo.domain.Pet;
import br.com.zelo.repo.ChatMessageRepository;
import br.com.zelo.repo.ChatSessionRepository;
import br.com.zelo.repo.ImageAnalysisRepository;
import br.com.zelo.service.BadRequestException;
import br.com.zelo.service.NotFoundException;
import br.com.zelo.service.PetService;
import br.com.zelo.service.TimelineService;
import br.com.zelo.service.UnavailableException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;

/**
 * Fluxo de análise visual: valida o arquivo (tamanho, tipo e assinatura), consulta o classificador e registra
 * SOMENTE o hash da imagem e o resultado (a foto em si não é armazenada).
 */
@Service
public class VisionService {

    private static final Logger LOG = LoggerFactory.getLogger(VisionService.class);
    private static final String HEALTHY_LABEL = "Healthy";
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final String DOG_ONLY =
            "O classificador visual foi treinado apenas com cães. Para gatos, descreva os sinais no chat que eu faço a triagem.";

    private final PetService pets;
    private final VisionClient client;
    private final ImageAnalysisRepository analyses;
    private final ChatSessionRepository sessions;
    private final ChatMessageRepository messages;
    private final TimelineService timeline;
    private final ObjectMapper mapper;
    private final Clock clock;

    public VisionService(PetService pets, VisionClient client, ImageAnalysisRepository analyses,
                         ChatSessionRepository sessions, ChatMessageRepository messages, TimelineService timeline,
                         ObjectMapper mapper, Clock clock) {
        this.pets = pets;
        this.client = client;
        this.analyses = analyses;
        this.sessions = sessions;
        this.messages = messages;
        this.timeline = timeline;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public VisionResult analyze(Long userId, String petId, Long sessionId, MultipartFile file) {
        Pet pet = pets.requireOwned(userId, petId);
        if (pet.getSpecies() != Species.DOG) {
            throw new BadRequestException(DOG_ONLY);
        }
        byte[] bytes = read(file);
        String extension = ImageSignature.detect(bytes)
                .orElseThrow(() -> new BadRequestException("Envie uma imagem JPG, PNG ou WebP válida"));

        VisionClient.Classification classification = classify(bytes, extension);
        VisionResult result = toResult(pets.contextFor(pet), classification);
        analyses.save(new ImageAnalysis(pet, sha256(bytes), write(result), LocalDateTime.now(clock)));
        timeline.record(pet, "vision", "Análise de foto (experimental): " + result.topLabelPt(),
                "Confiança " + Math.round(result.probability() * 100) + "%. " + result.nextAction().label() + ".",
                "vision", null, null);
        if (sessionId != null) {
            appendToConversation(userId, sessionId, pet, result);
        }
        return result;
    }

    private VisionClient.Classification classify(byte[] bytes, String extension) {
        try {
            VisionClient.Classification classification = client.classify(bytes, "foto." + extension);
            if (classification == null || classification.predictions() == null || classification.predictions().isEmpty()) {
                throw new UnavailableException("O serviço de análise de imagem não retornou um resultado");
            }
            return classification;
        } catch (RestClientException e) {
            LOG.warn("Serviço de visão indisponível: {}", e.getClass().getSimpleName());
            throw new UnavailableException(
                    "O serviço de análise de imagem está indisponível agora. Descreva o que você observa no chat.");
        }
    }

    private VisionResult toResult(PetContext pet, VisionClient.Classification c) {
        List<Prediction> predictions = c.predictions().stream()
                .map(p -> new Prediction(p.label(), p.labelPt(), round(p.probability()))).toList();
        Prediction top = predictions.get(0);
        boolean healthy = HEALTHY_LABEL.equals(top.label()) && !c.lowConfidence();
        Priority priority = healthy ? Priority.BAIXA : Priority.MEDIA;
        NextAction action = NextAction.forPriority(priority);
        return new VisionResult(pet.petId(), top.label(), top.labelPt(), top.probability(), predictions, c.lowConfidence(),
                priority, action, !healthy, c.modelVersion(), true, VisionResult.DISCLAIMER, message(pet, top, c, healthy),
                LocalDateTime.now(clock).atOffset(ZoneOffset.UTC));
    }

    private static String message(PetContext pet, Prediction top, VisionClient.Classification c, boolean healthy) {
        String pct = Math.round(top.probability() * 100) + "%";
        StringBuilder sb = new StringBuilder("Analisei a foto " + (pet.female() ? "da " : "do ") + pet.name()
                + ". Resultado experimental: " + top.labelPt().toLowerCase() + " (confiança " + pct + ").");
        if (c.lowConfidence()) {
            sb.append(" A confiança ficou baixa: tente outra foto, com boa luz e a região bem visível.");
        }
        sb.append(healthy
                ? " A pele parece saudável nesta imagem, mas uma foto não substitui o olhar de um veterinário."
                : " Isso é só um apoio, não um diagnóstico. Recomendo agendar uma consulta para avaliar a pele.");
        return sb.toString();
    }

    private void appendToConversation(Long userId, Long sessionId, Pet pet, VisionResult result) {
        ChatSession session = sessions.findByIdAndUserId(sessionId, userId)
                .filter(s -> s.getPet().getId().equals(pet.getId()))
                .orElseThrow(() -> new NotFoundException("Conversa não encontrada"));
        LocalDateTime now = LocalDateTime.now(clock);
        messages.save(new ChatMessage(session, Author.USER, "Enviei uma foto para análise.", null, now));
        messages.save(new ChatMessage(session, Author.BOT, result.message(),
                write(new VisionMeta("VISION", List.of("Relatar um sintoma", "Próximos cuidados"), result)), now));
    }

    private byte[] read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Nenhuma imagem enviada");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BadRequestException("A imagem excede o limite de 5 MB");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Não foi possível ler a imagem enviada");
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar o resultado da análise", e);
        }
    }

    /** Mesmo formato dos metadados do chat (kind, quick_replies, ...) com o resultado visual anexado. */
    private record VisionMeta(String kind, List<String> quickReplies, VisionResult vision) {
    }
}
