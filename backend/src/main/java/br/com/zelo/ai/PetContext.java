package br.com.zelo.ai;

import java.util.Set;

/**
 * Dados mínimos do pet que a IA pode receber (princípio da minimização: sem dados do tutor).
 *
 * @param ageMonths idade em meses; {@code null} quando desconhecida
 * @param female    usado apenas para concordância nas mensagens (ele/ela)
 */
public record PetContext(
        String petId,
        String name,
        Species species,
        Integer ageMonths,
        String breed,
        boolean brachycephalic,
        Double weightKg,
        boolean female,
        Set<String> historyTags) {

    private static final int PUPPY_MONTHS = 6;
    private static final int LARGE_DOG_KG = 25;
    private static final int SENIOR_DOG_YEARS = 8;
    private static final int SENIOR_LARGE_DOG_YEARS = 7;
    private static final int SENIOR_CAT_YEARS = 11;

    public PetContext {
        historyTags = historyTags == null ? Set.of() : Set.copyOf(historyTags);
    }

    public static PetContext unknown(Species species) {
        return new PetContext(null, "seu pet", species, null, null, false, null, false, Set.of());
    }

    public boolean isPuppy() {
        return ageMonths != null && ageMonths < PUPPY_MONTHS;
    }

    public boolean isSenior() {
        if (ageMonths == null) {
            return false;
        }
        int years = ageMonths / 12;
        if (species == Species.CAT) {
            return years >= SENIOR_CAT_YEARS;
        }
        boolean large = weightKg != null && weightKg > LARGE_DOG_KG;
        return years >= (large ? SENIOR_LARGE_DOG_YEARS : SENIOR_DOG_YEARS);
    }

    public boolean has(String historyTag) {
        return historyTags.contains(historyTag);
    }

    public String pronoun() {
        return female ? "ela" : "ele";
    }

    public String article() {
        return female ? "a" : "o";
    }
}
