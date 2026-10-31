package br.com.zelo.ai;

import java.util.List;

/**
 * Confiança da INTERPRETAÇÃO do relato (0–1), não probabilidade de doença.
 * Sobe com a quantidade de sinais claros, duração conhecida e perfil do pet; cai com erros de
 * digitação corrigidos e relatos vagos. Sinais de alerta explícitos têm piso de 0,90.
 */
public final class ConfidenceEstimator {

    private static final double BASE = 0.35;
    private static final double PER_SYMPTOM = 0.15;
    private static final int MAX_COUNTED_SYMPTOMS = 3;
    private static final double DURATION_BONUS = 0.08;
    private static final double PROFILE_BONUS = 0.05;
    private static final double TYPO_PENALTY = 0.05;
    private static final double VAGUE_CEILING = 0.45;
    private static final double RED_FLAG_FLOOR = 0.90;
    private static final double NO_SYMPTOM = 0.20;
    private static final double MAX = 0.97;

    private ConfidenceEstimator() {
    }

    public static double estimate(List<SymptomMention> active, boolean durationKnown, boolean profileKnown,
                                  int typoCorrections, boolean redFlag) {
        if (active.isEmpty()) {
            return NO_SYMPTOM;
        }
        boolean vague = active.stream().allMatch(m -> "mal_estar_inespecifico".equals(m.id()));
        double score = BASE + PER_SYMPTOM * Math.min(active.size(), MAX_COUNTED_SYMPTOMS);
        score += durationKnown ? DURATION_BONUS : 0;
        score += profileKnown ? PROFILE_BONUS : 0;
        score -= TYPO_PENALTY * Math.min(typoCorrections, 2);
        if (vague) {
            score = Math.min(score, VAGUE_CEILING);
        }
        if (redFlag) {
            score = Math.max(score, RED_FLAG_FLOOR);
        }
        return Math.round(Math.min(score, MAX) * 100.0) / 100.0;
    }
}
