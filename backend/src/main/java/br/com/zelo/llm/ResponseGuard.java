package br.com.zelo.llm;

import br.com.zelo.ai.Priority;
import br.com.zelo.ai.TextNormalizer;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Última barreira antes de mostrar texto gerado ao tutor. Rejeita (e o chat usa o rascunho seguro) respostas que
 * indicam dose ou medicamento, afirmam diagnóstico, minimizam a situação ("não precisa de veterinário") ou perdem
 * o encaminhamento profissional. Também só é aplicada a prioridades baixa/média: em alta/emergência o texto
 * determinístico é mantido sem reescrita.
 *
 * <p>É uma lista de bloqueio (heurística), não uma prova de segurança: por isso o resultado estruturado
 * (prioridade e ação) vem sempre do motor determinístico e o texto gerado é só apresentação.
 */
public final class ResponseGuard {

    private static final int MAX_LENGTH = 1_400;

    private static final Pattern DOSAGE = Pattern.compile(
            "\\b(?:\\d+(?:[.,]\\d+)?|meio|meia|um|uma|dois|duas|tres|quatro|cinco|dez)\\s?"
                    + "(?:mg|ml|mcg|ug|ui|g|gramas?|miligramas?|mililitros?|gotas?|comprimidos?|capsulas?|ampolas?|colher(?:es)?|saches?|doses?)\\b"
                    + "|\\ba cada \\d+ horas\\b");

    private static final String DISEASES = "cinomose|parvovirose|parvo|leptospirose|giardia|giardiase|erliquiose|babesiose|cushing"
            + "|diabetes|insuficiencia renal|cancer|tumor maligno|raiva|fiv|felv|pancreatite|gastrite|verminose|otite|dermatite"
            + "|alergia|infeccao|intoxicacao|envenenamento|torcao|cistite|piometra|atopia|sarna|tinha|micose|hepatite|artrose|displasia"
            + "|cardiopatia|insuficiencia cardiaca|pneumonia|bronquite|tosse dos canis|traqueobronquite|calazar|leishmaniose";
    private static final Pattern DISEASE = Pattern.compile("\\b(?:" + DISEASES + ")\\b");
    private static final Pattern DIAGNOSIS = Pattern.compile(
            "\\bo diagnostico (?:e|eh|certo|correto|provavel)\\b|\\bdiagnosticad[oa]\\b|\\bconfirm\\w* (?:o )?diagnostico\\b"
                    + "|\\bcom certeza (?:e|eh|tem)\\b|\\bcertamente (?:e|eh|tem)\\b");

    private static final List<String> DRUGS = List.of(
            "dipirona", "novalgina", "paracetamol", "tylenol", "ibuprofeno", "advil", "tramadol", "amoxicilina", "prednisolona",
            "prednisona", "meloxicam", "cetoprofeno", "dexametasona", "ivermectina", "buscopan", "dramin", "diazepam", "cefalexina",
            "metronidazol", "omeprazol", "enrofloxacino", "carprofeno", "firocoxib", "gabapentina", "acepromazina", "aspirina",
            "naproxeno", "diclofenaco", "nimesulida", "loratadina", "benadryl", "hidroxizina", "ondansetrona", "metoclopramida",
            "plasil", "penicilina", "ciprofloxacino", "dorflex", "neosaldina", "clonazepam", "rivotril", "carvao ativado",
            "antibiotico", "anti-inflamatorio", "antiinflamatorio", "vermifugo caseiro");

    private static final Pattern DISMISSIVE = Pattern.compile(
            "\\bnao (?:e|eh|precisa|ha|tem|existe|vejo)\\b(?:\\s+\\w+){0,3}\\s+(?:urgen\\w*|emergenc\\w*|veterinari\\w*|clinica|consulta|pressa|motivo|preocupa\\w*)"
                    + "|\\bpode esperar\\b|\\bsem pressa\\b|\\bfique tranquil[oa]\\b|\\bnao se preocupe\\b|\\bnao e (?:grave|nada)\\b"
                    + "|\\bnao precisa (?:ir|levar|procurar|de)\\b");
    private static final Pattern URGENCY = Pattern.compile("urgen|emergenc|agora|imediat|pronto.?atend|sem esperar");
    private static final Pattern PROFESSIONAL = Pattern.compile("veterinari|clinica");
    private static final Pattern CODE_FENCE = Pattern.compile("```");
    private static final Pattern MARKDOWN_MARKS = Pattern.compile("\\*\\*|__|^#{1,6}\\s+", Pattern.MULTILINE);
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]");

    private ResponseGuard() {
    }

    /** O chat exibe texto simples: remove negrito/títulos em markdown que o modelo costuma acrescentar. */
    public static String sanitize(String reply) {
        return reply == null ? "" : MARKDOWN_MARKS.matcher(reply).replaceAll("").trim();
    }

    public record Verdict(boolean accepted, String reason) {
        static Verdict ok() {
            return new Verdict(true, "ok");
        }

        static Verdict reject(String reason) {
            return new Verdict(false, reason);
        }
    }

    /**
     * @param draft rascunho seguro; nomes de medicamentos ou doenças que já aparecem nele (ex.: na recusa de
     *              prescrição) são permitidos
     */
    public static Verdict check(String reply, Priority priority, String draft) {
        if (reply == null || reply.isBlank()) {
            return Verdict.reject("vazia");
        }
        if (reply.length() > MAX_LENGTH) {
            return Verdict.reject("longa demais");
        }
        if (CODE_FENCE.matcher(reply).find()) {
            return Verdict.reject("bloco de código inesperado");
        }
        String normalized = TextNormalizer.normalize(reply);
        String normalizedDraft = TextNormalizer.normalize(draft);
        if (DOSAGE.matcher(normalized).find()) {
            return Verdict.reject("menciona dose");
        }
        if (DIAGNOSIS.matcher(normalized).find() || mentionsNewDisease(normalized, normalizedDraft)) {
            return Verdict.reject("afirma ou sugere diagnóstico");
        }
        String drug = firstNewDrug(normalized, normalizedDraft);
        if (drug != null) {
            return Verdict.reject("menciona medicamento: " + drug);
        }
        if (priority == Priority.EMERGENCIA && !URGENCY.matcher(normalized).find()) {
            return Verdict.reject("perdeu o sinal de urgência");
        }
        if (priority.level() >= Priority.MEDIA.level()) {
            if (!PROFESSIONAL.matcher(normalized).find()) {
                return Verdict.reject("não encaminha a um profissional");
            }
            if (DISMISSIVE.matcher(normalized).find()) {
                return Verdict.reject("minimiza a situação");
            }
        }
        return Verdict.ok();
    }

    private static boolean mentionsNewDisease(String normalized, String normalizedDraft) {
        var matcher = DISEASE.matcher(normalized);
        while (matcher.find()) {
            if (!normalizedDraft.contains(matcher.group())) {
                return true;
            }
        }
        return false;
    }

    /** Compara sem espaços nem pontuação: "d i p i r o n a" e "dipi-rona" não escapam da lista. */
    private static String firstNewDrug(String normalized, String normalizedDraft) {
        String compactReply = NON_ALNUM.matcher(normalized).replaceAll("");
        String compactDraft = NON_ALNUM.matcher(normalizedDraft).replaceAll("");
        for (String drug : DRUGS) {
            String compactDrug = NON_ALNUM.matcher(TextNormalizer.normalize(drug)).replaceAll("");
            if (compactReply.contains(compactDrug) && !compactDraft.contains(compactDrug)) {
                return drug;
            }
        }
        return null;
    }
}
