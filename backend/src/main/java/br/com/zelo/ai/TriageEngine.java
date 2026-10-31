package br.com.zelo.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.regex.Pattern;

/**
 * Orquestra a camada determinística: extração → duração → regras de prioridade → confiança.
 * Sem estado e sem Spring: toda a lógica clínica pode ser testada em milissegundos.
 */
public final class TriageEngine {

    public static final String RULES_VERSION = "zelo-rules-1.0";

    private static final Pattern REASSURING = Pattern.compile(
            "\\b(?:esta|ta|estao|anda)\\s+(?:bem|normal|otim[oa]|tranquil[oa]|animad[oa]|feliz)\\b"
                    + "|\\bcomendo bem\\b|\\bbrincando\\b|\\bnormal\\b|\\bsem problemas?\\b|\\btudo bem\\b");
    /** "não está animado" não é tranquilizador: a frase negada sai antes do teste de tranquilidade. */
    private static final Pattern NEGATED_PHRASE = Pattern.compile(
            "\\b(?:nao|nunca|nem)\\s+(?:(?:esta|estao|anda|ta|fica|tem|ficou)\\s+)?\\w+");
    private static final double UNRECOGNIZED_CONFIDENCE = 0.20;

    private final SymptomExtractor extractor;
    private final PriorityEngine priorityEngine;

    public TriageEngine(SymptomExtractor extractor, PriorityEngine priorityEngine) {
        this.extractor = extractor;
        this.priorityEngine = priorityEngine;
    }

    /**
     * @param priorActive    sintomas já coletados nos turnos anteriores da conversa
     * @param priorDuration  duração já informada em turnos anteriores (ou {@code null})
     */
    public TriageAnalysis analyze(String text, PetContext pet, List<SymptomMention> priorActive, Integer priorDuration) {
        return analyze(text, pet, priorActive, priorDuration, List.of());
    }

    /**
     * @param extraSymptoms sintomas adicionais sugeridos pelo LLM; entram como presentes e só podem AUMENTAR
     *                      a prioridade (o motor determinístico continua sendo o piso de segurança)
     */
    public TriageAnalysis analyze(String text, PetContext pet, List<SymptomMention> priorActive, Integer priorDuration,
                                  List<SymptomMention> extraSymptoms) {
        SymptomExtractor.Extraction extraction = extractor.extract(text, pet.species());
        List<SymptomMention> combined = new ArrayList<>(extraction.mentions());
        extraSymptoms.stream().filter(e -> combined.stream().noneMatch(m -> m.id().equals(e.id()))).forEach(combined::add);
        List<SymptomMention> active = merge(priorActive, combined);
        Integer duration = mergeDuration(priorDuration, DurationParser.parseHours(extraction.normalizedText()));
        PriorityEngine.Decision decision = priorityEngine.decide(active, pet, duration);

        String withoutNegations = NEGATED_PHRASE.matcher(extraction.normalizedText()).replaceAll(" ");
        boolean unrecognized = active.isEmpty() && extraction.negated().isEmpty()
                && !extraction.normalizedText().isBlank() && !REASSURING.matcher(withoutNegations).find();
        if (unrecognized) {
            return unrecognized(extraction, duration);
        }
        boolean redFlag = !decision.redFlagIds().isEmpty();
        boolean vague = !active.isEmpty() && active.stream().allMatch(m -> "mal_estar_inespecifico".equals(m.id()));
        double confidence = ConfidenceEstimator.estimate(active, duration != null, pet.ageMonths() != null,
                extraction.typoCorrections().size(), redFlag);
        return new TriageAnalysis(extraction.normalizedText(), active, extraction.negated(), decision.priority(),
                decision.reasons(), decision.redFlagIds(), decision.contextFactors(), decision.rules(),
                duration, confidence, vague, false, extraction.typoCorrections());
    }

    /** Ausência de sinal reconhecido NÃO é evidência de baixo risco: prioridade média + revisão humana. */
    private static TriageAnalysis unrecognized(SymptomExtractor.Extraction extraction, Integer duration) {
        return new TriageAnalysis(extraction.normalizedText(), List.of(), List.of(), Priority.MEDIA,
                List.of("Não consegui identificar sinais específicos no relato; por precaução, a orientação é avaliar com a clínica"),
                List.of(), List.of(), List.of("R13_RELATO_NAO_RECONHECIDO"), duration, UNRECOGNIZED_CONFIDENCE,
                false, true, extraction.typoCorrections());
    }

    /** Une o que já se sabia com o que veio agora; uma negação nova ("não vomita mais") remove o sintoma. */
    private static List<SymptomMention> merge(List<SymptomMention> prior, List<SymptomMention> current) {
        Map<String, SymptomMention> merged = new LinkedHashMap<>();
        prior.forEach(m -> merged.put(m.id(), m));
        for (SymptomMention m : current) {
            if (m.negated()) {
                merged.remove(m.id());
            } else {
                merged.put(m.id(), m);
            }
        }
        return new ArrayList<>(merged.values());
    }

    private static Integer mergeDuration(Integer prior, OptionalInt current) {
        if (current.isEmpty()) {
            return prior;
        }
        return prior == null ? current.getAsInt() : Math.max(prior, current.getAsInt());
    }
}
