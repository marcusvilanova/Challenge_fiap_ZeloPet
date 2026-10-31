package br.com.zelo.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Corrige erros simples de digitação ("vomtou" → "vomitou") comparando cada palavra com um
 * vocabulário pequeno e distintivo de termos clínicos. Conservador de propósito: só corrige
 * palavras com 6+ letras, mesma letra inicial e distância de Damerau-Levenshtein baixa.
 */
public final class TypoCorrector {

    private static final int MIN_LENGTH = 6;
    private static final int LONG_WORD = 9;

    private final Set<String> vocabulary;
    private final Predicate<String> recognized;

    /**
     * @param recognized diz se o catálogo já reconhece a palavra (ex.: "vomitos" casa com {@code vomit\w*});
     *                   palavras reconhecidas nunca são "corrigidas"
     */
    public TypoCorrector(Set<String> vocabulary, Predicate<String> recognized) {
        this.vocabulary = Set.copyOf(vocabulary);
        this.recognized = recognized;
    }

    public TypoCorrector(Set<String> vocabulary) {
        this(vocabulary, token -> false);
    }

    public record Result(String text, List<String> corrections) {
    }

    public Result correct(String normalized) {
        String[] tokens = normalized.split(" ");
        List<String> corrections = new ArrayList<>();
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            if (token.length() < MIN_LENGTH || vocabulary.contains(token) || recognized.test(token)) {
                continue;
            }
            String best = closest(token);
            if (best != null) {
                corrections.add(token + "→" + best);
                tokens[i] = best;
            }
        }
        return new Result(String.join(" ", tokens), List.copyOf(corrections));
    }

    private String closest(String token) {
        int maxDistance = token.length() >= LONG_WORD ? 2 : 1;
        String best = null;
        int bestDistance = maxDistance + 1;
        for (String word : vocabulary) {
            if (word.charAt(0) != token.charAt(0) || Math.abs(word.length() - token.length()) > maxDistance) {
                continue;
            }
            int distance = distance(token, word);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = word;
            }
        }
        return best;
    }

    /** Distância de Damerau-Levenshtein (transposição adjacente conta como 1). */
    static int distance(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[a.length()][b.length()];
    }
}
