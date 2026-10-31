package br.com.zelo.ai;

import java.util.List;

/**
 * Resultado interno e completo da análise determinística de um relato (ou de uma conversa).
 *
 * @param durationHours há quantas horas os sinais começaram; {@code null} se não informado
 * @param vague         só houve queixa inespecífica ("está estranho")
 * @param unrecognized  o relato não trouxe nenhum sinal reconhecido nem tranquilizador: ausência de
 *                      sintoma reconhecido não é evidência de baixo risco, então a prioridade é média
 */
public record TriageAnalysis(
        String normalizedText,
        List<SymptomMention> active,
        List<SymptomMention> negated,
        Priority priority,
        List<String> reasons,
        List<String> redFlagIds,
        List<String> contextFactors,
        List<String> rules,
        Integer durationHours,
        double confidence,
        boolean vague,
        boolean unrecognized,
        List<String> typoCorrections) {

    private static final double REVIEW_CONFIDENCE_THRESHOLD = 0.60;

    /**
     * Garante o piso determinístico: quando o LLM soma sintomas e a segunda análise ficaria ABAIXO da primeira
     * (ex.: relato não reconhecido = média; LLM sugere só "espirros" = baixa), a prioridade da primeira prevalece
     * e a revisão humana continua exigida.
     */
    public TriageAnalysis withFloor(TriageAnalysis floor) {
        if (priority.level() >= floor.priority().level()) {
            return this;
        }
        List<String> mergedReasons = new java.util.ArrayList<>(floor.reasons());
        reasons.stream().filter(r -> !mergedReasons.contains(r)).forEach(mergedReasons::add);
        List<String> mergedRules = new java.util.ArrayList<>(floor.rules());
        mergedRules.add("R14_PISO_DETERMINISTICO");
        return new TriageAnalysis(normalizedText, active, negated, floor.priority(), List.copyOf(mergedReasons),
                redFlagIds, contextFactors, List.copyOf(mergedRules), durationHours, confidence, vague,
                unrecognized || floor.unrecognized(), typoCorrections);
    }

    public boolean hasSymptoms() {
        return !active.isEmpty();
    }

    public boolean emergency() {
        return priority == Priority.EMERGENCIA;
    }

    /** Revisão humana: prioridade alta/emergência, confiança baixa ou queixa vaga. */
    public boolean humanReviewRequired() {
        return priority.level() >= Priority.ALTA.level() || confidence < REVIEW_CONFIDENCE_THRESHOLD || vague
                || unrecognized;
    }
}
