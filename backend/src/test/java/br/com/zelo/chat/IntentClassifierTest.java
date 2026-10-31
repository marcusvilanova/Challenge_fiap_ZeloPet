package br.com.zelo.chat;

import br.com.zelo.ai.TextNormalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** O consentimento (LGPD) não pode nascer de linguagem solta: estes casos vieram da revisão de segurança. */
class IntentClassifierTest {

    private final IntentClassifier classifier = new IntentClassifier();

    private Intent classify(String text) {
        return classifier.classify(TextNormalizer.normalize(text));
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" NÃO concede compartilhamento")
    @ValueSource(strings = {
            "Não quero compartilhar com a clínica",
            "Eu não quero compartilhar",
            "nunca vou compartilhar isso com ninguém da clínica",
            "agora não",
            "Pode ser grave? Ele está vomitando sangue",
            "Quero saber se ele pode comer ração",
            "posso compartilhar a conversa com minha esposa?",
            "ok, mas e a vacina dele?",
            "sim, ele vomitou de novo e está abatido"})
    @DisplayName("recusas, perguntas e relatos que só parecem 'sim' nunca viram consentimento")
    void looseLanguage_isNeverConsent(String text) {
        assertThat(classify(text)).isNotIn(Intent.YES, Intent.SHARE);
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" = recusa")
    @ValueSource(strings = {"agora não", "não", "Não quero compartilhar com a clínica", "não obrigada", "depois"})
    void refusals_areNo(String text) {
        assertThat(classify(text)).isEqualTo(Intent.NO);
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" = sim")
    @ValueSource(strings = {"sim", "Sim, compartilhar com a clínica", "claro", "pode sim", "autorizo", "Quero sim"})
    @DisplayName("afirmações curtas e os botões de resposta rápida valem como 'sim'")
    void shortAffirmations_areYes(String text) {
        assertThat(classify(text)).isEqualTo(Intent.YES);
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" = pedido explícito")
    @ValueSource(strings = {"Enviar para a clínica avaliar", "quero compartilhar com a clínica", "compartilhar com a clínica",
            "pode enviar para a clínica"})
    void explicitCommands_areShare(String text) {
        assertThat(classify(text)).isEqualTo(Intent.SHARE);
    }

    @Test
    @DisplayName("sintoma começando com negação não é recusa de compartilhamento")
    void symptomStartingWithNo_isNotPlainRefusal() {
        assertThat(classify("não está comendo")).isNotEqualTo(Intent.NO);
    }

    @Test
    @DisplayName("pedido de medicamento e demais intenções seguem funcionando")
    void otherIntents_stillWork() {
        assertThat(classify("posso dar dipirona pra ela?")).isEqualTo(Intent.MEDICATION_REQUEST);
        assertThat(classify("oi, tudo bem?")).isEqualTo(Intent.GREETING);
        assertThat(classify("obrigada!")).isEqualTo(Intent.THANKS);
        assertThat(classify("quais as próximas vacinas?")).isEqualTo(Intent.CARE_PLAN);
        assertThat(classify("o que você faz?")).isEqualTo(Intent.HELP);
        assertThat(classify("")).isEqualTo(Intent.OTHER);
    }
}
