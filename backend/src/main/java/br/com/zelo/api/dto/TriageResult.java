package br.com.zelo.api.dto;

import br.com.zelo.ai.NextAction;
import br.com.zelo.ai.Priority;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Saída estruturada da triagem (contrato da Sprint 3, evoluído para v1.0). Toda saída carrega
 * model_version, confidence, human_review_required e disclaimer, e é persistida junto do evento.
 */
public record TriageResult(
        String triageId,
        String petId,
        List<String> symptoms,
        List<SymptomDetail> symptomDetails,
        Priority priority,
        NextAction nextAction,
        String nextActionLabel,
        String reason,
        List<String> reasons,
        List<String> redFlags,
        List<String> contextFactors,
        double confidence,
        String modelVersion,
        Engine engine,
        boolean humanReviewRequired,
        String disclaimer,
        Integer durationHours,
        boolean needsMoreInfo,
        OffsetDateTime createdAt,
        boolean sharedWithClinic) {

    public static final String DISCLAIMER = "Esta orientação não substitui avaliação veterinária.";
    public static final String MODEL_VERSION = "zelo-triage-hybrid-1.0";

    /**
     * @param referenceFrequency fração de cães/gatos do dataset D2 com o sintoma — contexto, nunca risco de doença
     */
    public record SymptomDetail(String id, String label, String category, String evidence, Double referenceFrequency) {
    }

    /** @param llm modelo generativo usado nesta resposta, ou "local-fallback" */
    public record Engine(String rules, String knowledge, String llm) {
    }

    public TriageResult withShared(boolean shared) {
        return new TriageResult(triageId, petId, symptoms, symptomDetails, priority, nextAction, nextActionLabel, reason,
                reasons, redFlags, contextFactors, confidence, modelVersion, engine, humanReviewRequired, disclaimer,
                durationHours, needsMoreInfo, createdAt, shared);
    }

    public TriageResult withTriageId(String newTriageId) {
        return new TriageResult(newTriageId, petId, symptoms, symptomDetails, priority, nextAction, nextActionLabel, reason,
                reasons, redFlags, contextFactors, confidence, modelVersion, engine, humanReviewRequired, disclaimer,
                durationHours, needsMoreInfo, createdAt, sharedWithClinic);
    }
}
