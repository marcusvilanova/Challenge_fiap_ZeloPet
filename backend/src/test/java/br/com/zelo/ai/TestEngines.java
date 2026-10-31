package br.com.zelo.ai;

import java.util.List;
import java.util.Set;

/** Monta o motor determinístico sem Spring, com a base de conhecimento real do projeto. */
public final class TestEngines {

    public static final KnowledgeBase KNOWLEDGE = KnowledgeBase.loadDefault();
    public static final SymptomExtractor EXTRACTOR =
            new SymptomExtractor(KNOWLEDGE, new TypoCorrector(KNOWLEDGE.typoVocabulary(), KNOWLEDGE::recognizesWord));
    public static final TriageEngine ENGINE = new TriageEngine(EXTRACTOR, new PriorityEngine());

    private TestEngines() {
    }

    public static PetContext dog() {
        return pet(Species.DOG, 48, 15.0, false, Set.of());
    }

    public static PetContext cat() {
        return pet(Species.CAT, 48, 4.0, false, Set.of());
    }

    public static PetContext pet(Species species, Integer ageMonths, Double weightKg, boolean brachy, Set<String> history) {
        return new PetContext("PET-0001", "Mel", species, ageMonths, null, brachy, weightKg, true, history);
    }

    public static TriageAnalysis analyze(String text, PetContext pet) {
        return ENGINE.analyze(text, pet, List.of(), null);
    }
}
