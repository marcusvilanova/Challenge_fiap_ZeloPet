package br.com.zelo.ai;

import br.com.zelo.ai.KnowledgeBase.SymptomDef;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai sintomas de um relato em português: normaliza, corrige digitação, aplica os padrões do
 * catálogo, resolve sobreposições/famílias e detecta negação ("não vomitou", "a tosse passou").
 *
 * <p>Princípio de segurança: na dúvida, o sintoma conta como presente. Negar errado esconde um
 * sinal de alerta; deixar de negar apenas aumenta a cautela.
 */
public final class SymptomExtractor {

    private static final int NEGATION_WINDOW = 3;
    private static final int RED_FLAG_NEGATION_WINDOW = 2;
    private static final int RESOLUTION_WINDOW = 3;
    private static final Pattern TOKEN = Pattern.compile("\\S+");
    private static final Set<String> NEGATORS = Set.of("nao", "sem", "nunca", "nem", "nenhum", "nenhuma", "jamais", "nada");
    private static final Set<String> STOPPED_VERBS = Set.of("parou", "para", "deixou", "deixa");
    private static final Set<String> RESOLVED_MARKERS = Set.of("passou", "sumiu", "cessou", "acabou", "parou", "melhorou");
    /** Depois de "passou/parou", estes termos indicam que o sinal continua ou voltou. */
    private static final Pattern RECURRENCE = Pattern.compile("\\b(?:voltou|voltaram|volta|novamente|de novo|outra vez|piorou)\\b");
    private static final int RECURRENCE_WINDOW = 4;
    /** "e"/"ou" interrompem o escopo: "sem fôlego e tosse" não nega a tosse (na dúvida, o sinal conta). */
    private static final Set<String> BREAKERS = Set.of("mas", "porem", "entretanto", "contudo", "todavia", "e", "ou", "|");

    private final KnowledgeBase knowledge;
    private final TypoCorrector typoCorrector;

    public SymptomExtractor(KnowledgeBase knowledge, TypoCorrector typoCorrector) {
        this.knowledge = knowledge;
        this.typoCorrector = typoCorrector;
    }

    public record Extraction(String normalizedText, List<SymptomMention> mentions, List<String> typoCorrections) {

        public List<SymptomMention> active() {
            return mentions.stream().filter(m -> !m.negated()).toList();
        }

        public List<SymptomMention> negated() {
            return mentions.stream().filter(SymptomMention::negated).toList();
        }
    }

    public Extraction extract(String rawText, Species species) {
        TypoCorrector.Result typo = typoCorrector.correct(TextNormalizer.normalize(rawText));
        String text = typo.text();
        List<SymptomMention> hits = dropContained(findHits(text, species));
        List<SymptomMention> negationApplied = hits.stream().map(h -> h.withNegated(isNegated(text, h, hits))).toList();
        // a família é resolvida DEPOIS da negação: "vomitou, sem sangue no vômito" mantém o vômito presente
        return new Extraction(text, collapseById(resolveFamilies(negationApplied)), typo.corrections());
    }

    private List<SymptomMention> findHits(String text, Species species) {
        List<SymptomMention> hits = new ArrayList<>();
        for (SymptomDef def : knowledge.symptoms()) {
            for (Pattern pattern : def.patterns()) {
                Matcher m = pattern.matcher(text);
                while (m.find()) {
                    hits.add(new SymptomMention(def.id(), def.label(), def.category(), def.severityFor(species),
                            m.group().trim(), false, m.start(), m.end()));
                }
            }
        }
        return hits;
    }

    /** Remove acertos contidos em outro acerto igual ou mais grave (ex.: "vomitou sangue" engole "sangue"). */
    private List<SymptomMention> dropContained(List<SymptomMention> hits) {
        List<SymptomMention> ordered = hits.stream()
                .sorted(Comparator.comparingInt((SymptomMention h) -> h.end() - h.start()).reversed()
                        .thenComparing(Comparator.comparingInt(SymptomMention::severity).reversed())
                        .thenComparingInt(h -> catalogIndex(h.id())))
                .toList();
        List<SymptomMention> accepted = new ArrayList<>();
        for (SymptomMention hit : ordered) {
            boolean swallowed = accepted.stream().anyMatch(a -> a.start() <= hit.start() && hit.end() <= a.end()
                    && a.severity() >= hit.severity());
            if (!swallowed) {
                accepted.add(hit);
            }
        }
        return accepted;
    }

    private int catalogIndex(String id) {
        List<SymptomDef> all = knowledge.symptoms();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id().equals(id)) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    /**
     * Em uma família (ex.: vômito / vômito repetido / vômito com sangue) vale apenas o mais grave entre os sintomas
     * PRESENTES. Uma negação de membro mais grave é mantida (informa o que o tutor descartou); negações de membros
     * mais leves que um sintoma presente são descartadas.
     */
    private List<SymptomMention> resolveFamilies(List<SymptomMention> hits) {
        Map<String, SymptomMention> strongestActiveByFamily = new LinkedHashMap<>();
        for (SymptomMention hit : hits) {
            String family = knowledge.symptom(hit.id()).family();
            if (family != null && !hit.negated()) {
                strongestActiveByFamily.merge(family, hit, (a, b) -> b.severity() > a.severity() ? b : a);
            }
        }
        return hits.stream().filter(h -> {
            String family = knowledge.symptom(h.id()).family();
            SymptomMention strongest = family == null ? null : strongestActiveByFamily.get(family);
            if (strongest == null) {
                return true;
            }
            return h.negated() ? h.severity() > strongest.severity() : strongest.id().equals(h.id());
        }).toList();
    }

    private boolean isNegated(String text, SymptomMention hit, List<SymptomMention> all) {
        return negatedByPrecedingTerm(text, hit, all) || resolvedByFollowingTerm(text, hit);
    }

    private boolean negatedByPrecedingTerm(String text, SymptomMention hit, List<SymptomMention> all) {
        int clauseStart = text.lastIndexOf('|', hit.start()) + 1;
        if (clauseStart >= hit.start()) {
            return false;
        }
        List<int[]> tokens = tokenSpans(text, clauseStart, hit.start());
        List<String> words = tokens.stream().map(s -> text.substring(s[0], s[1])).toList();
        int window = hit.redFlag() ? RED_FLAG_NEGATION_WINDOW : NEGATION_WINDOW;
        for (int i = tokens.size() - 1, seen = 0; i >= 0 && seen < window; i--, seen++) {
            if (BREAKERS.contains(words.get(i)) || belongsToOtherHit(tokens.get(i)[0], hit, all)) {
                return false;
            }
            String next = i + 1 < words.size() ? words.get(i + 1) : "";
            String previous = i > 0 ? words.get(i - 1) : "";
            if (isNegator(words.get(i), previous, next)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNegator(String token, String previous, String next) {
        if (STOPPED_VERBS.contains(token)) {
            return "de".equals(next) && !"nao".equals(previous); // "não parou de espirrar" = continua
        }
        if ("nao".equals(token)) {
            return !"sei".equals(next) && !STOPPED_VERBS.contains(next); // "não sei se foi…" = incerteza
        }
        return NEGATORS.contains(token);
    }

    private static boolean belongsToOtherHit(int tokenStart, SymptomMention self, List<SymptomMention> all) {
        return all.stream().anyMatch(o -> o != self && o.start() <= tokenStart && tokenStart < o.end());
    }

    private boolean resolvedByFollowingTerm(String text, SymptomMention hit) {
        int clauseEnd = text.indexOf('|', hit.end());
        int end = clauseEnd < 0 ? text.length() : clauseEnd;
        List<int[]> tokens = tokenSpans(text, hit.end(), end);
        for (int i = 0; i < tokens.size() && i < RESOLUTION_WINDOW; i++) {
            String token = text.substring(tokens.get(i)[0], tokens.get(i)[1]);
            if (BREAKERS.contains(token)) {
                return false; // "o vômito melhorou e a tosse…": o marcador pertence a outro sintoma
            }
            if (!RESOLVED_MARKERS.contains(token)) {
                continue;
            }
            String previous = i > 0 ? text.substring(tokens.get(i - 1)[0], tokens.get(i - 1)[1]) : "";
            String next = i + 1 < tokens.size() ? text.substring(tokens.get(i + 1)[0], tokens.get(i + 1)[1]) : "";
            int lastWord = Math.min(tokens.size() - 1, i + RECURRENCE_WINDOW);
            String afterMarker = text.substring(tokens.get(i)[1], tokens.get(lastWord)[1]);
            boolean continues = NEGATORS.contains(previous) || "ainda".equals(previous) // "não passou", "ainda não parou"
                    || ("parou".equals(token) && "de".equals(next))                     // "parou de comer" é outro sinal
                    || ("passou".equals(token) && "mal".equals(next))                   // "passou mal" = piorou
                    || RECURRENCE.matcher(afterMarker).find();                          // "parou ontem mas hoje voltou"
            return !continues;
        }
        return false;
    }

    private static List<int[]> tokenSpans(String text, int from, int to) {
        List<int[]> spans = new ArrayList<>();
        Matcher m = TOKEN.matcher(text).region(from, to);
        while (m.find()) {
            spans.add(new int[] {m.start(), m.end()});
        }
        return spans;
    }

    /** Um sintoma por id: se alguma ocorrência não foi negada, ele conta como presente. */
    private static List<SymptomMention> collapseById(List<SymptomMention> mentions) {
        Map<String, SymptomMention> merged = new LinkedHashMap<>();
        mentions.stream().sorted(Comparator.comparingInt(SymptomMention::start))
                .forEach(m -> merged.merge(m.id(), m, (a, b) -> a.negated() && !b.negated() ? b : a));
        return List.copyOf(merged.values());
    }
}
