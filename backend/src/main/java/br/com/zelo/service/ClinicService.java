package br.com.zelo.service;

import br.com.zelo.ai.Priority;
import br.com.zelo.api.dto.ApiDtos.ClinicTriageDetail;
import br.com.zelo.api.dto.ApiDtos.ClinicTriageSummary;
import br.com.zelo.api.dto.ApiDtos.ConversationLine;
import br.com.zelo.api.dto.ApiDtos.ReviewRequest;
import br.com.zelo.api.dto.ApiDtos.ReviewResponse;
import br.com.zelo.api.dto.TriageResult;
import br.com.zelo.domain.AppUser;
import br.com.zelo.domain.ChatMessage;
import br.com.zelo.domain.ClinicReview;
import br.com.zelo.domain.ClinicReview.Decision;
import br.com.zelo.domain.Triage;
import br.com.zelo.repo.AppUserRepository;
import br.com.zelo.repo.ChatMessageRepository;
import br.com.zelo.repo.ClinicReviewRepository;
import br.com.zelo.repo.TriageRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Painel da clínica: enxerga SOMENTE triagens compartilhadas com consentimento ativo, e registra a revisão
 * profissional (humano no circuito). A IA organiza e prioriza; a decisão clínica é do médico-veterinário.
 */
@Service
public class ClinicService {

    private static final int QUEUE_LIMIT = 100;
    private static final int TIMELINE_LIMIT = 10;

    private final TriageRepository triages;
    private final ClinicReviewRepository reviews;
    private final AppUserRepository users;
    private final TriageService triageService;
    private final PetService pets;
    private final TimelineService timeline;
    private final ChatMessageRepository messages;
    private final Clock clock;

    public ClinicService(TriageRepository triages, ClinicReviewRepository reviews, AppUserRepository users,
                         TriageService triageService, PetService pets, TimelineService timeline,
                         ChatMessageRepository messages, Clock clock) {
        this.messages = messages;
        this.triages = triages;
        this.reviews = reviews;
        this.users = users;
        this.triageService = triageService;
        this.pets = pets;
        this.timeline = timeline;
        this.clock = clock;
    }

    /** Fila de atenção: maior prioridade primeiro; dentro da mesma prioridade, a mais recente. */
    @Transactional(readOnly = true)
    public List<ClinicTriageSummary> queue() {
        List<Triage> visible = triages.findVisibleToClinic(PageRequest.of(0, QUEUE_LIMIT));
        Set<Long> reviewed = visible.isEmpty() ? Set.of()
                : reviews.findReviewedTriageIds(visible.stream().map(Triage::getId).toList());
        return visible.stream()
                .map(t -> summary(t, reviewed.contains(t.getId())))
                .sorted(Comparator.comparing((ClinicTriageSummary s) -> s.priority().level()).reversed()
                        .thenComparing(ClinicTriageSummary::createdAt, Comparator.reverseOrder()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ClinicTriageDetail detail(String publicId) {
        Triage triage = requireVisible(publicId);
        boolean reviewed = reviews.findFirstByTriageIdOrderByCreatedAtDesc(triage.getId()).isPresent();
        return new ClinicTriageDetail(summary(triage, reviewed), triageService.toResult(triage),
                pets.toResponse(triage.getPet()), triage.getInputText(),
                timeline.latestForClinic(triage.getPet().getId(), TIMELINE_LIMIT), reviewsOf(triage),
                conversationOf(triage));
    }

    /**
     * Conversa que levou à triagem: da saudação até a resposta do bot que trouxe ESTA triagem (inclusive).
     * O que o tutor disse depois (outros assuntos, outras triagens) não é mostrado à clínica (minimização).
     */
    private List<ConversationLine> conversationOf(Triage triage) {
        if (triage.getSession() == null) {
            return List.of(); // triagem avulsa (POST /api/triage): não há conversa
        }
        String marker = "\"" + triage.getPublicId() + "\"";
        List<ConversationLine> lines = new ArrayList<>();
        for (ChatMessage message : messages.findBySessionIdOrderByIdAsc(triage.getSession().getId())) {
            lines.add(new ConversationLine(message.getAuthor().name(), message.getContent(),
                    message.getCreatedAt().atOffset(ZoneOffset.UTC)));
            if (message.getMetaJson() != null && message.getMetaJson().contains(marker)) {
                break;
            }
        }
        return lines;
    }

    @Transactional
    public ReviewResponse review(Long reviewerId, ReviewRequest request) {
        Triage triage = requireVisible(request.triageId());
        if (request.decision() == Decision.AJUSTADA && request.finalPriority() == triage.getPriority()) {
            throw new BadRequestException("Para ajustar, escolha uma prioridade diferente da sugerida pela IA");
        }
        if (request.decision() == Decision.CONFIRMADA && request.finalPriority() != triage.getPriority()) {
            throw new BadRequestException("Para confirmar, mantenha a prioridade sugerida pela IA");
        }
        AppUser reviewer = users.findById(reviewerId).orElseThrow(() -> new NotFoundException("Usuário não encontrado"));
        ClinicReview saved = reviews.save(new ClinicReview(triage, reviewer, request.decision(), request.finalPriority(),
                blankToNull(request.note()), LocalDateTime.now(clock)));
        timeline.record(triage.getPet(), "review", reviewTitle(saved),
                saved.getNote() == null ? "Revisão registrada pela clínica." : saved.getNote(), "clinic", triage.getId(), null);
        return toResponse(saved);
    }

    private Triage requireVisible(String publicId) {
        try {
            return triages.findVisibleToClinicById(Triage.parsePublicId(publicId))
                    .orElseThrow(() -> new NotFoundException("Triagem não encontrada ou não compartilhada"));
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("Triagem não encontrada ou não compartilhada");
        }
    }

    private ClinicTriageSummary summary(Triage triage, boolean reviewed) {
        TriageResult result = triageService.toResult(triage);
        var pet = triage.getPet();
        Integer ageMonths = pets.toResponse(pet).ageMonths();
        return new ClinicTriageSummary(triage.getPublicId(), pet.getPublicId(), pet.getName(), pet.getSpecies().wire(),
                ageMonths, pet.getOwner().getDisplayName(), triage.getPriority(), result.symptoms(),
                result.symptomDetails().stream().map(TriageResult.SymptomDetail::label).toList(),
                triage.isHumanReviewRequired(), reviewed, triage.getCreatedAt().atOffset(ZoneOffset.UTC));
    }

    private List<ReviewResponse> reviewsOf(Triage triage) {
        return reviews.findByTriageIdOrderByCreatedAtDesc(triage.getId()).stream().map(this::toResponse).toList();
    }

    private ReviewResponse toResponse(ClinicReview review) {
        return new ReviewResponse(review.getId(), review.getTriage().getPublicId(), review.getReviewer().getDisplayName(),
                review.getDecision().name(), review.getFinalPriority(), review.getNote(),
                review.getCreatedAt().atOffset(ZoneOffset.UTC));
    }

    private static String reviewTitle(ClinicReview review) {
        Priority p = review.getFinalPriority();
        return review.getDecision() == Decision.CONFIRMADA
                ? "Clínica confirmou a prioridade " + p.wire()
                : "Clínica ajustou a prioridade para " + p.wire();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
