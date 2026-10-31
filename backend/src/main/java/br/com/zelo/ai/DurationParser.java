package br.com.zelo.ai;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Estima há quantas horas os sinais começaram, a partir do texto normalizado.
 * O ponto delicado é separar duração ("há 3 dias", "tem 3 dias que vomita") de idade do pet
 * ("filhote de 3 meses", "tem 8 semanas").
 */
public final class DurationParser {

    private static final int HOURS_PER_DAY = 24;
    private static final int HOURS_PER_WEEK = 168;
    private static final int HOURS_PER_MONTH = 720;

    private static final Map<String, Integer> NUMBER_WORDS = Map.ofEntries(
            Map.entry("um", 1), Map.entry("uma", 1), Map.entry("dois", 2), Map.entry("duas", 2),
            Map.entry("tres", 3), Map.entry("quatro", 4), Map.entry("cinco", 5), Map.entry("seis", 6),
            Map.entry("sete", 7), Map.entry("oito", 8), Map.entry("nove", 9), Map.entry("dez", 10),
            Map.entry("doze", 12), Map.entry("quinze", 15), Map.entry("vinte", 20));

    private static final String NUMBER = "(\\d{1,3}|um|uma|dois|duas|tres|quatro|cinco|seis|sete|oito|nove|dez|doze|quinze|vinte)";
    private static final Pattern QUANTIFIED = Pattern.compile(
            "\\b(mais de )?" + NUMBER + "(?: ou " + NUMBER + ")? (horas?|dias?|semanas?|mes|meses)\\b");

    /** Antes do número: indica idade para unidades longas (semanas/meses) ou curtas (horas/dias). */
    private static final Pattern AGE_CUE_LONG = Pattern.compile("\\b(?:tem|com|de|idade|aos)\\s*$");
    /** "está com 3 dias sem comer" é duração; "filhote com 45 dias" é idade (só vale "com" colado a um substantivo de pet). */
    private static final Pattern AGE_CUE_SHORT = Pattern.compile(
            "\\b(?:idade|aos|(?:filhote|filhota|bebe|cachorrinh[oa]|gatinh[oa]|cachorro|cao|gato|gata|cadela)\\s+com)\\s*$");
    private static final int CONTEXT_CHARS = 20;
    private static final Pattern AGE_AFTER = Pattern.compile("^ (?:de )?(?:idade|vida|nascid[oa])");

    private record Landmark(Pattern pattern, int hours) {
        static Landmark of(String regex, int hours) {
            return new Landmark(Pattern.compile(regex), hours);
        }
    }

    private static final List<Landmark> LANDMARKS = List.of(
            Landmark.of("\\b(?:hoje|esta manha|essa manha|de manha|agora|ha pouco|faz pouco)\\b", 8),
            Landmark.of("\\b(?:madrugada|noite passada|ontem a noite)\\b", 14),
            Landmark.of("\\bontem\\b", HOURS_PER_DAY),
            Landmark.of("\\banteontem\\b", 2 * HOURS_PER_DAY),
            Landmark.of("\\b(?:varios|alguns) dias\\b|\\b(?:ha|faz) dias\\b", 3 * HOURS_PER_DAY),
            Landmark.of("\\b(?:(?:ha|faz|por|durante) (?:varias |algumas |muitas )?|ultimas )semanas\\b", 2 * HOURS_PER_WEEK),
            Landmark.of("\\b(?:(?:ha|faz|por|durante) (?:varios |alguns |muitos )?|ultimos )meses\\b", HOURS_PER_MONTH));

    private DurationParser() {
    }

    /** Maior duração mencionada (a mais conservadora para a triagem). */
    public static OptionalInt parseHours(String normalized) {
        int best = -1;
        Matcher q = QUANTIFIED.matcher(normalized);
        while (q.find()) {
            if (!isAge(normalized, q)) {
                best = Math.max(best, quantifiedHours(q));
            }
        }
        for (Landmark landmark : LANDMARKS) {
            if (landmark.pattern().matcher(normalized).find()) {
                best = Math.max(best, landmark.hours());
            }
        }
        return best < 0 ? OptionalInt.empty() : OptionalInt.of(best);
    }

    private static boolean isAge(String text, Matcher m) {
        String before = text.substring(Math.max(0, m.start() - CONTEXT_CHARS), m.start());
        String after = text.substring(m.end(), Math.min(text.length(), m.end() + 14));
        if (after.startsWith(" que") || after.startsWith(" atras")) {
            return false; // "tem 3 dias que ele vomita"
        }
        if (AGE_AFTER.matcher(after).find()) {
            return true;
        }
        if (after.startsWith(" de ") || after.startsWith(" sem ")) {
            return false; // "com 2 dias de diarreia", "cachorro com 3 dias sem comer": duração
        }
        boolean longUnit = m.group(4).startsWith("semana") || m.group(4).startsWith("mes");
        return (longUnit ? AGE_CUE_LONG : AGE_CUE_SHORT).matcher(before).find();
    }

    private static int quantifiedHours(Matcher m) {
        int first = number(m.group(2));
        int amount = m.group(3) == null ? first : Math.max(first, number(m.group(3)));
        int hours = amount * unitHours(m.group(4));
        return m.group(1) != null ? hours + 1 : hours; // "mais de 2 dias" passa de 48 h
    }

    private static int number(String token) {
        return token.chars().allMatch(Character::isDigit) ? Integer.parseInt(token) : NUMBER_WORDS.get(token);
    }

    private static int unitHours(String unit) {
        if (unit.startsWith("hora")) {
            return 1;
        }
        if (unit.startsWith("dia")) {
            return HOURS_PER_DAY;
        }
        if (unit.startsWith("semana")) {
            return HOURS_PER_WEEK;
        }
        return HOURS_PER_MONTH;
    }
}
