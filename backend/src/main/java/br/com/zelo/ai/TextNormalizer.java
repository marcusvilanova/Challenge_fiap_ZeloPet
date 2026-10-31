package br.com.zelo.ai;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalização de texto do tutor. Espelha ia/triage/textnorm.py: minúsculas, sem acentos,
 * pontuação de fim de oração vira " | " (limite de oração para a detecção de negação).
 */
public final class TextNormalizer {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern CLAUSE_BREAK = Pattern.compile("[.,;:!?\\n\\r()\\[\\]{}…]+");
    private static final Pattern REPEATED = Pattern.compile("([a-z])\\1{2,}");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9| ]+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private TextNormalizer() {
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String s = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        s = DIACRITICS.matcher(s).replaceAll("");
        s = REPEATED.matcher(s).replaceAll("$1");
        s = CLAUSE_BREAK.matcher(s).replaceAll(" | ");
        s = s.replace("'", " ");
        s = NON_ALNUM.matcher(s).replaceAll(" ");
        return SPACES.matcher(s).replaceAll(" ").trim();
    }
}
