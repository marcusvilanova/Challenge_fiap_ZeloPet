package br.com.zelo.ai;

/**
 * Sintoma encontrado no relato.
 *
 * @param severity severidade efetiva (1–4) já considerando a espécie
 * @param negated  {@code true} quando o tutor negou ou disse que o sintoma passou
 * @param start    posição no texto normalizado (usado só para resolver sobreposições)
 */
public record SymptomMention(
        String id,
        String label,
        String category,
        int severity,
        String evidence,
        boolean negated,
        int start,
        int end) {

    public SymptomMention withNegated(boolean value) {
        return new SymptomMention(id, label, category, severity, evidence, value, start, end);
    }

    public boolean redFlag() {
        return severity >= Priority.EMERGENCIA.level();
    }
}
