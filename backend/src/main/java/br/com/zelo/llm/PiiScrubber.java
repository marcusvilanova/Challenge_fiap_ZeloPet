package br.com.zelo.llm;

import java.util.regex.Pattern;

/**
 * Mascara dados pessoais que o tutor possa digitar no relato (CPF, telefone, e-mail, CEP) ANTES de enviar o texto
 * a um provedor externo de IA. Minimização de dados (LGPD): o modelo só precisa dos sinais clínicos.
 */
public final class PiiScrubber {

    public static final String MASK = "[dado removido]";

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+");
    private static final Pattern CPF = Pattern.compile("\\b\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}\\b");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+?55\\s?)?\\(?\\d{2}\\)?[\\s.-]?9?\\d{4}[\\s.-]?\\d{4}(?!\\d)");
    private static final Pattern CEP = Pattern.compile("\\b\\d{5}-?\\d{3}\\b");

    private PiiScrubber() {
    }

    public static String scrub(String text) {
        if (text == null) {
            return "";
        }
        String result = EMAIL.matcher(text).replaceAll(MASK);
        result = CPF.matcher(result).replaceAll(MASK);
        result = PHONE.matcher(result).replaceAll(MASK);
        return CEP.matcher(result).replaceAll(MASK);
    }
}
