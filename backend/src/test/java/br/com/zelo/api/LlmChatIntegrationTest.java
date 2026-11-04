package br.com.zelo.api;

import br.com.zelo.llm.LlmClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Caminho com LLM habilitado: extração que só soma sintomas (e nunca reduz a prioridade), reescrita validada pelo
 * guard SOMENTE para prioridades baixa/média, e falhas com fallback para o texto determinístico.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:zelo-llm-${random.uuid};DB_CLOSE_DELAY=-1",
        "zelo.seed.enabled=true",
        "zelo.seed.demo-password=" + ApiTestSupport.DEMO_PASSWORD,
        "zelo.seed.clinic-scenarios=false", // este teste conta as chamadas ao LLM falso
        "zelo.rate-limit.per-minute=1000", "zelo.rate-limit.login-per-minute=1000",
        "zelo.vision.url=http://localhost:1"})
@AutoConfigureMockMvc
class LlmChatIntegrationTest {

    @TestConfiguration
    static class FakeLlmConfig {
        @Bean
        @Primary
        LlmClient fakeLlm() {
            return new FakeLlm();
        }
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;

    private ApiTestSupport api;
    private MockHttpSession ana;
    private String mel;
    private long sessionId;

    @BeforeEach
    void setUp() throws Exception {
        FakeLlm.reset();
        api = new ApiTestSupport(mvc, mapper);
        ana = api.login("ana@zelo.demo");
        mel = api.petIdOf(ana, "Mel");
        sessionId = api.postJson(ana, "/api/chat/sessions", "{\"pet_id\":\"" + mel + "\",\"fresh\":true}").get("session_id").asLong();
    }

    private JsonNode say(String text) throws Exception {
        return api.postJson(ana, "/api/chat/messages",
                mapper.writeValueAsString(mapper.createObjectNode().put("pet_id", mel).put("session_id", sessionId).put("text", text)));
    }

    /** Tosse + duração + "respira normalmente" = triagem de prioridade média (as duas perguntas de acompanhamento). */
    private JsonNode reachMediumTriage() throws Exception {
        say("ela está com tosse");
        say("Há 1 ou 2 dias");
        return say("Respira normalmente");
    }

    @Test
    @DisplayName("léxico fraco + LLM: o sintoma sugerido entra, a triagem sobe e a evidência mostra que veio da IA")
    void weakLexicon_llmAddsSymptomAndRaisesPriority() throws Exception {
        FakeLlm.extraction = List.of("gengivas_palidas");

        JsonNode reply = say("o bichinho tá com um jeito esquisito e meio azulado");

        JsonNode triage = reply.get("reply").get("triage");
        assertThat(triage.get("priority").asText()).isEqualTo("emergencia");
        assertThat(triage.get("symptom_details")).extracting(s -> s.get("evidence").asText()).contains("(identificado pela IA)");
        assertThat(triage.get("engine").get("llm").asText()).isEqualTo("fake-llm-1");
        assertThat(FakeLlm.EXTRACTION_CALLS.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("piso determinístico: relato não reconhecido (média) + LLM sugerindo só um sintoma leve NÃO vira baixa")
    void llmCannotLowerAnUnrecognizedReport() throws Exception {
        FakeLlm.extraction = List.of("espirros");

        JsonNode triage = api.postJson(ana, "/api/triage", "{\"pet_id\":\"" + mel + "\",\"text\":\"meu cachorro tem um negócio no zóio\"}");

        assertThat(triage.get("priority").asText()).isEqualTo("media");
        assertThat(triage.get("human_review_required").asBoolean()).isTrue();
        assertThat(FakeLlm.EXTRACTION_CALLS.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("léxico forte: o LLM de extração nem é chamado")
    void strongLexicon_doesNotCallExtraction() throws Exception {
        say("ela teve uma convulsão");

        assertThat(FakeLlm.EXTRACTION_CALLS.get()).isZero();
    }

    @Test
    @DisplayName("alta/emergência: o texto determinístico é mantido e o LLM de reescrita NEM é chamado")
    void highPriority_isNeverRewritten() throws Exception {
        FakeLlm.rewrite = Optional.of("Pode ficar tranquila, é normal.");

        JsonNode reply = say("ela teve uma convulsão");

        assertThat(FakeLlm.REWRITE_CALLS.get()).isZero();
        assertThat(reply.get("reply").get("llm_status").asText()).isEqualTo("deterministic_high_priority");
        assertThat(reply.get("reply").get("text").asText()).contains("emergência").doesNotContain("é normal");
        assertThat(reply.get("reply").get("triage").get("priority").asText()).isEqualTo("emergencia");
    }

    @Test
    @DisplayName("prioridade média: reescrita segura é aceita e substitui o rascunho")
    void mediumPriority_safeRewriteIsUsed() throws Exception {
        String rewritten = "A Mel está com tosse há alguns dias. Vale marcar uma consulta com a clínica veterinária nos próximos dias.";
        FakeLlm.rewrite = Optional.of(rewritten);

        JsonNode reply = reachMediumTriage();

        assertThat(reply.get("reply").get("triage").get("priority").asText()).isEqualTo("media");
        assertThat(reply.get("reply").get("text").asText()).isEqualTo(rewritten);
        assertThat(reply.get("reply").get("llm_status").asText()).isEqualTo("used");
        assertThat(reply.get("reply").get("triage").get("engine").get("llm").asText()).isEqualTo("fake-llm-1");
    }

    @Test
    @DisplayName("média: reescrita com dose de remédio é reprovada pelo guard e o rascunho seguro é mantido")
    void mediumPriority_unsafeRewriteIsRejected() throws Exception {
        FakeLlm.rewrite = Optional.of("Dê 10 mg de dipirona e depois procure a clínica veterinária.");

        JsonNode reply = reachMediumTriage();

        assertThat(reply.get("reply").get("llm_status").asText()).isEqualTo("rejected_fallback");
        assertThat(reply.get("reply").get("text").asText()).contains("triagem preliminar").doesNotContain("dipirona");
    }

    @Test
    @DisplayName("média: reescrita que minimiza ('não é urgente, não precisa de veterinário') é reprovada")
    void mediumPriority_dismissiveRewriteIsRejected() throws Exception {
        FakeLlm.rewrite = Optional.of("Isso não é urgente e não precisa de veterinário agora, fique tranquila.");

        JsonNode reply = reachMediumTriage();

        assertThat(reply.get("reply").get("llm_status").asText()).isEqualTo("rejected_fallback");
        assertThat(reply.get("reply").get("text").asText()).contains("Vale agendar uma consulta");
    }

    @Test
    @DisplayName("LLM indisponível: a resposta local é entregue e o estado registra o fallback")
    void llmUnavailable_fallsBackToDraft() throws Exception {
        FakeLlm.rewrite = Optional.empty();

        JsonNode reply = reachMediumTriage();

        assertThat(reply.get("reply").get("llm_status").asText()).isEqualTo("error_fallback");
        assertThat(reply.get("reply").get("text").asText()).contains("Vale agendar uma consulta");
    }
}
