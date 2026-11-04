package br.com.zelo.api.dto;

import br.com.zelo.ai.Priority;
import br.com.zelo.domain.ClinicReview;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Contratos da API REST (JSON em snake_case). Entradas validadas com Bean Validation. */
public final class ApiDtos {

    private ApiDtos() {
    }

    // ------------------------------------------------------------------------------ autenticação
    public record RegisterRequest(
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(min = 8, max = 72) String password) {
    }

    public record UserResponse(Long id, String name, String email, String role) {
    }

    // ----------------------------------------------------------------------------------- pets
    public record CreatePetRequest(
            @NotBlank @Size(max = 80) String name,
            @NotBlank String species,
            @Size(max = 80) String breed,
            @NotNull Boolean female,
            @PastOrPresent LocalDate birthDate,
            @DecimalMin("0.1") @DecimalMax("120") BigDecimal weightKg,
            List<@Size(max = 40) String> historyTags) {
    }

    public record PetResponse(String petId, String name, String species, String breed, boolean female,
                              LocalDate birthDate, Integer ageMonths, BigDecimal weightKg, List<String> historyTags,
                              boolean brachycephalic, boolean sharingEnabled) {
    }

    public record CareItemResponse(Long id, String type, String name, LocalDate lastDone, LocalDate nextDue,
                                   String status, long daysUntilDue) {
    }

    /** Novo item do plano de cuidados (tipo: VACINA, VERMIFUGO, ANTIPULGAS ou CONSULTA). */
    public record CareItemRequest(
            @NotBlank String type,
            @NotBlank @Size(max = 120) String name,
            @PastOrPresent LocalDate lastDone,
            @NotNull LocalDate nextDue) {
    }

    /** Marca um cuidado como feito; sem {@code next_due}, a próxima data segue o intervalo padrão do tipo. */
    public record CareDoneRequest(LocalDate nextDue) {
    }

    public record TimelineEventResponse(Long id, String type, String title, String summary, String source,
                                        String refId, OffsetDateTime occurredAt) {
    }

    public record PetCatalog(List<Option> dogBreeds, List<Option> catBreeds, List<Option> historyTags) {
    }

    public record Option(String id, String label) {
    }

    // ------------------------------------------------------------------------------------ chat
    public record ChatRequest(
            @NotBlank String petId,
            Long sessionId,
            @NotBlank @Size(max = 1000) String text) {
    }

    public record ChatMessageResponse(Long id, String author, String text, List<String> quickReplies,
                                      TriageResult triage, VisionResult vision, String kind, String llmStatus,
                                      OffsetDateTime createdAt) {
    }

    /** @param sharedTriageId triagem que passou a ser compartilhada neste turno (para a interface atualizar o cartão) */
    public record ChatReply(Long sessionId, ChatMessageResponse reply, String stage, String sharedTriageId) {
    }

    public record ChatHistory(Long sessionId, List<ChatMessageResponse> messages) {
    }

    public record TriageRequest(
            @NotBlank String petId,
            @NotBlank @Size(max = 1000) String text) {
    }

    // -------------------------------------------------------------------------- consentimento
    public record ConsentRequest(@NotBlank String petId, @Size(max = 60) String scope) {
    }

    public record ConsentResponse(Long id, String petId, String scope, String version, String recipient,
                                  OffsetDateTime grantedAt, OffsetDateTime revokedAt, boolean active) {
    }

    // ----------------------------------------------------------------------------------- clínica
    public record ClinicTriageSummary(String triageId, String petId, String petName, String species, Integer petAgeMonths,
                                      String tutorName, Priority priority, List<String> symptoms,
                                      List<String> symptomLabels, boolean humanReviewRequired, boolean reviewed, OffsetDateTime createdAt) {
    }

    public record ClinicTriageDetail(ClinicTriageSummary summary, TriageResult triage, PetResponse pet, String inputText,
                                     List<TimelineEventResponse> timeline, List<ReviewResponse> reviews) {
    }

    public record ReviewRequest(
            @NotBlank String triageId,
            @NotNull ClinicReview.Decision decision,
            @NotNull Priority finalPriority,
            @Size(max = 1000) String note) {
    }

    public record ReviewResponse(Long id, String triageId, String reviewer, String decision, Priority finalPriority,
                                 String note, OffsetDateTime createdAt) {
    }

    // ------------------------------------------------------------------------------- sistema
    public record SystemStatus(String knowledgeVersion, String rulesVersion, String llm, boolean llmEnabled,
                               boolean visionAvailable, String visionModel) {
    }
}
