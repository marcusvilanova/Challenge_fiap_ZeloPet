package br.com.zelo.api.dto;

import br.com.zelo.ai.NextAction;
import br.com.zelo.ai.Priority;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Resultado EXPERIMENTAL da análise visual de pele (somente cães). Nunca é diagnóstico.
 *
 * @param probability probabilidade da classe mais provável (0–1)
 */
public record VisionResult(
        String petId,
        String topLabel,
        String topLabelPt,
        double probability,
        List<Prediction> predictions,
        boolean lowConfidence,
        Priority priority,
        NextAction nextAction,
        boolean humanReviewRequired,
        String modelVersion,
        boolean experimental,
        String disclaimer,
        String message,
        OffsetDateTime createdAt) {

    public static final String DISCLAIMER =
            "Classificação experimental de imagem. Não é diagnóstico e não substitui a avaliação veterinária.";

    public record Prediction(String label, String labelPt, double probability) {
    }
}
