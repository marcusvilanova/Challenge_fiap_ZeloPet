package br.com.zelo.llm;

import br.com.zelo.ai.Priority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseGuardTest {

    private static final String DRAFT = "Pelo que você descreveu, o ideal é falar com a clínica ainda hoje. Isto é uma triagem preliminar.";

    @Test
    @DisplayName("resposta segura e que mantém o encaminhamento é aceita")
    void safeReply_isAccepted() {
        String reply = "Entendo a preocupação com a Mel. O ideal é falar com a clínica veterinária ainda hoje.";

        assertThat(ResponseGuard.check(reply, Priority.ALTA, DRAFT).accepted()).isTrue();
    }

    @ParameterizedTest(name = "[{index}] rejeita: {0}")
    @ValueSource(strings = {
            "Dê 10 mg de dipirona para ela e fale com a clínica.",
            "Pode dar 2 comprimidos de paracetamol; depois procure a clínica veterinária.",
            "Dê ibuprofeno e procure o veterinário.",
            "O diagnóstico é parvovirose, procure a clínica veterinária.",
            "Com certeza é cinomose, fale com o veterinário.",
            "Ele tem parvovirose, leve na clínica."})
    @DisplayName("rejeita dose, medicamento e diagnóstico afirmado")
    void unsafeContent_isRejected(String reply) {
        assertThat(ResponseGuard.check(reply, Priority.ALTA, DRAFT).accepted()).isFalse();
    }

    @Test
    @DisplayName("emergência sem sinal de urgência é rejeitada; com urgência é aceita")
    void emergency_requiresUrgencyLanguage() {
        String calm = "Fique tranquila, converse com a clínica veterinária quando puder.";
        String urgent = "Leve a Mel agora a um pronto-atendimento veterinário.";

        assertThat(ResponseGuard.check(calm, Priority.EMERGENCIA, DRAFT).accepted()).isFalse();
        assertThat(ResponseGuard.check(urgent, Priority.EMERGENCIA, DRAFT).accepted()).isTrue();
    }

    @Test
    @DisplayName("prioridade média ou maior precisa encaminhar a um profissional")
    void mediumOrHigher_requiresProfessionalReferral() {
        assertThat(ResponseGuard.check("Observe com carinho e descanse.", Priority.MEDIA, DRAFT).accepted()).isFalse();
        assertThat(ResponseGuard.check("Observe com carinho e descanse.", Priority.BAIXA, DRAFT).accepted()).isTrue();
    }

    @Test
    @DisplayName("nome de medicamento que já está no rascunho (recusa de prescrição) é permitido")
    void drugNameAlreadyInDraft_isAllowed() {
        String draft = "Não posso indicar remédios como dipirona. Procure a clínica veterinária.";
        String reply = "Não posso indicar remédios, como a dipirona, que faz mal a pets. Procure a clínica veterinária.";

        assertThat(ResponseGuard.check(reply, Priority.ALTA, draft).accepted()).isTrue();
    }

    @Test
    @DisplayName("vazia, longa demais ou com bloco de código é rejeitada")
    void malformedReplies_areRejected() {
        assertThat(ResponseGuard.check("  ", Priority.BAIXA, DRAFT).accepted()).isFalse();
        assertThat(ResponseGuard.check("a".repeat(1500), Priority.BAIXA, DRAFT).accepted()).isFalse();
        assertThat(ResponseGuard.check("```json {} ```", Priority.BAIXA, DRAFT).accepted()).isFalse();
    }

    @Test
    @DisplayName("sanitize remove negrito e títulos de markdown")
    void sanitize_stripsMarkdown() {
        assertThat(ResponseGuard.sanitize("## Aviso\n**Atenção:** procure a clínica.")).isEqualTo("Aviso\nAtenção: procure a clínica.");
        assertThat(ResponseGuard.sanitize(null)).isEmpty();
    }
}
