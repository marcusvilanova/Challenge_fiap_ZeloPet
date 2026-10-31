package br.com.zelo.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Base de conhecimento da triagem, gerada por ia/triage/build_knowledge.py a partir do
 * catálogo de sintomas da equipe e do dataset D2. Imutável após o carregamento.
 */
public final class KnowledgeBase {

    public static final String DEFAULT_RESOURCE = "/triage/triage_knowledge.json";

    private final String version;
    private final List<SymptomDef> symptoms;
    private final Map<String, SymptomDef> byId;
    private final Map<Species, List<BreedEntry>> breeds;
    private final List<HistoryTag> historyTags;
    private final D2Info d2;

    private KnowledgeBase(KnowledgeFile file) {
        this.version = file.version();
        this.d2 = file.d2();
        this.symptoms = file.symptoms().stream().map(SymptomDef::from).toList();
        this.byId = symptoms.stream().collect(Collectors.toUnmodifiableMap(SymptomDef::id, Function.identity()));
        this.breeds = file.breeds().entrySet().stream().collect(Collectors.toUnmodifiableMap(
                e -> Species.fromString(e.getKey()), e -> List.copyOf(e.getValue())));
        this.historyTags = List.copyOf(file.historyTags());
    }

    public static KnowledgeBase loadDefault() {
        try (InputStream in = KnowledgeBase.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(
                        "Recurso " + DEFAULT_RESOURCE + " não encontrado. Rode ia/triage/build_knowledge.py.");
            }
            return new KnowledgeBase(new ObjectMapper().readValue(in, KnowledgeFile.class));
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler a base de conhecimento da triagem", e);
        }
    }

    public String version() {
        return version;
    }

    public List<SymptomDef> symptoms() {
        return symptoms;
    }

    public SymptomDef symptom(String id) {
        SymptomDef def = byId.get(id);
        if (def == null) {
            throw new IllegalArgumentException("Sintoma desconhecido: " + id);
        }
        return def;
    }

    public boolean knows(String id) {
        return byId.containsKey(id);
    }

    public List<BreedEntry> breeds(Species species) {
        return breeds.getOrDefault(species, List.of());
    }

    public boolean isBrachycephalic(Species species, String breed) {
        return breed != null && breeds(species).stream()
                .anyMatch(b -> b.brachycephalic() && b.id().equalsIgnoreCase(breed.trim()));
    }

    public List<HistoryTag> historyTags() {
        return historyTags;
    }

    public D2Info d2() {
        return d2;
    }

    /** {@code true} se algum padrão do catálogo reconhece a palavra isolada (ex.: "vomitos"). */
    public boolean recognizesWord(String token) {
        return symptoms.stream().anyMatch(s -> s.patterns().stream().anyMatch(p -> p.matcher(token).find()));
    }

    /** Palavras-chave (≥6 letras) usadas apenas para corrigir erros de digitação. */
    public Set<String> typoVocabulary() {
        return symptoms.stream().flatMap(s -> s.typoWords().stream())
                .filter(w -> w.length() >= 6).collect(Collectors.toUnmodifiableSet());
    }

    // ------------------------------------------------------------------ tipos do arquivo JSON
    @JsonIgnoreProperties(ignoreUnknown = true)
    record KnowledgeFile(String version, D2Info d2, List<SymptomEntry> symptoms,
                         Map<String, List<BreedEntry>> breeds, List<HistoryTag> historyTags) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record D2Info(int rowsRaw, int rowsClean, int duplicateRowsRemoved) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SymptomEntry(String id, String label, String category, int severity, Integer severityCat,
                        Integer severityDog, String family, List<String> patterns,
                        List<String> typoWords, D2Stats d2) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record D2Stats(int mentions, Map<String, Double> prevalence) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BreedEntry(String id, String label, boolean brachycephalic) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HistoryTag(String id, String label, String d2Value) {
    }

    /** Sintoma já com expressões regulares compiladas. */
    public record SymptomDef(String id, String label, String category, int baseSeverity,
                             Integer severityCat, Integer severityDog, String family,
                             List<Pattern> patterns, List<String> typoWords, D2Stats d2) {

        static SymptomDef from(SymptomEntry e) {
            List<Pattern> compiled = e.patterns().stream().map(Pattern::compile).toList();
            List<String> typo = e.typoWords() == null ? List.of() : List.copyOf(e.typoWords());
            return new SymptomDef(e.id(), e.label(), e.category(), e.severity(), e.severityCat(),
                    e.severityDog(), e.family(), compiled, typo, e.d2());
        }

        public int severityFor(Species species) {
            Integer override = species == Species.CAT ? severityCat : severityDog;
            return override != null ? override : baseSeverity;
        }

        /** Frequência do sintoma no D2 para a espécie (0–1); apenas contexto, nunca risco de doença. */
        public double referenceFrequency(Species species) {
            if (d2 == null || d2.prevalence() == null) {
                return 0;
            }
            return d2.prevalence().getOrDefault(species.wire().toLowerCase(Locale.ROOT), 0.0);
        }
    }
}
