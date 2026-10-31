package br.com.zelo.chat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Classificador de intenções por regras sobre o texto já normalizado (sem acentos, minúsculo).
 *
 * <p>Regra de segurança (LGPD): consentimento NUNCA é inferido de linguagem solta. "Sim" só vale se a mensagem
 * inteira for uma afirmação curta; pedidos de compartilhar só valem como comando explícito, sem negação e sem
 * ser pergunta. Qualquer negação perto de "compartilhar/clínica" é recusa.
 */
public final class IntentClassifier {

    private static final Pattern NEGATOR = Pattern.compile("\\b(?:nao|nunca|jamais|nem|sem|dispenso|depois|deixa)\\b");
    private static final Pattern SHARE_CONTEXT = Pattern.compile("compartilh\\w*|\\bclinica\\b|autoriz\\w*|\\benviar?\\b");

    private static final Pattern NO_PLAIN = Pattern.compile(
            "^(?:nao|agora nao|depois|talvez depois|dispenso|deixa|nao quero|nao obrigad[oa]|nao precisa)"
                    + "(?: \\|)?(?: obrigad[oa])?$");
    private static final Pattern YES_STRICT = Pattern.compile(
            "^(?:sim|claro|pode|pode sim|quero|quero sim|aceito|autorizo|ok|okay|com certeza|certo|beleza)"
                    + "(?: \\|)?(?: por favor)?(?: compartilhar(?: com a clinica)?)?$");
    private static final Pattern SHARE_STRICT = Pattern.compile(
            "^(?:(?:eu )?(?:quero|gostaria de|pode|vou|por favor)\\s+)*(?:compartilhar|enviar|mandar)\\b(?:\\s+\\w+){0,6}"
                    + "\\s+(?:para|pra|com)\\s+(?:a\\s+)?clinica(?:\\s+\\w+){0,2}$");

    private static final Pattern MEDICATION = Pattern.compile(
            "\\b(?:posso|pode|podemos|devo|deveria)\\s+(?:\\w+\\s+){0,3}(?:dar|usar|colocar|passar|tomar|medicar|aplicar)\\b"
                    + "|\\bqual\\s+(?:\\w+\\s+){0,2}(?:remedio|medicamento|dose|dosagem|antibiotico)\\b"
                    + "|\\bque\\s+(?:remedio|medicamento)\\b|\\bdosagem\\b|\\bdose de\\b|\\bquantas? gotas\\b"
                    + "|\\bquantos? (?:ml|comprimidos?)\\b|\\bme (?:indica|recomenda|receita)\\b"
                    + "|\\bremedio (?:para|pra) (?:ele|ela|o|a)\\b");

    private static final Map<Intent, Pattern> OTHERS = new LinkedHashMap<>();

    static {
        OTHERS.put(Intent.FINISH, Pattern.compile(
                "^(?:so isso|e so|e isso|pronto|finalizar|finaliza|terminar|era so isso|nada mais|mais nada|so por enquanto)\\b"));
        OTHERS.put(Intent.NEW_REPORT, Pattern.compile(
                "^(?:relatar|descrever|novo relato|quero relatar|vou relatar|queria relatar)\\b"));
        OTHERS.put(Intent.THANKS, Pattern.compile("\\b(?:obrigad[oa]|valeu|agradec\\w*|brigad[oa])\\b"));
        OTHERS.put(Intent.FAREWELL, Pattern.compile("^(?:tchau|ate (?:logo|mais|amanha)|flw|falou)\\b"));
        OTHERS.put(Intent.HELP, Pattern.compile(
                "\\b(?:ajuda|o que (?:voce )?(?:faz|pode)|como funciona|quem (?:e|eh) voce|para que serve|menu)\\b"));
        OTHERS.put(Intent.CARE_PLAN, Pattern.compile(
                "\\b(?:vacina\\w*|vermifug\\w*|antipulga\\w*|proximos? cuidados?|plano de cuidados?|calendario|carteirinha|ciclo zelo)\\b"));
        OTHERS.put(Intent.GREETING, Pattern.compile("^(?:oi+|ola|opa|e ai|bom dia|boa tarde|boa noite|hey|hello|salve)\\b"));
    }

    public Intent classify(String normalized) {
        if (normalized.isBlank()) {
            return Intent.OTHER;
        }
        boolean negated = NEGATOR.matcher(normalized).find();
        if (NO_PLAIN.matcher(normalized).find() || (negated && SHARE_CONTEXT.matcher(normalized).find())) {
            return Intent.NO;
        }
        if (MEDICATION.matcher(normalized).find()) {
            return Intent.MEDICATION_REQUEST;
        }
        if (!negated && SHARE_STRICT.matcher(normalized).find()) {
            return Intent.SHARE;
        }
        if (YES_STRICT.matcher(normalized).find()) {
            return Intent.YES;
        }
        for (Map.Entry<Intent, Pattern> entry : OTHERS.entrySet()) {
            if (entry.getValue().matcher(normalized).find()) {
                return entry.getKey();
            }
        }
        return Intent.OTHER;
    }
}
