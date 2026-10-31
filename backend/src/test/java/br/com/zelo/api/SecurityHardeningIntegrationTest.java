package br.com.zelo.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Regressões dos achados da revisão de segurança (consentimento, limitador, vazamento de dados, docs). */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:zelo-sec-${random.uuid};DB_CLOSE_DELAY=-1",
        "zelo.seed.enabled=true",
        "zelo.seed.demo-password=" + ApiTestSupport.DEMO_PASSWORD,
        "zelo.llm.provider=local",
        "zelo.rate-limit.per-minute=1000", "zelo.rate-limit.login-per-minute=1000",
        "zelo.vision.url=http://localhost:1"})
@AutoConfigureMockMvc
class SecurityHardeningIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;

    private ApiTestSupport api() {
        return new ApiTestSupport(mvc, mapper);
    }

    private JsonNode say(MockHttpSession session, String petId, long sessionId, String text) throws Exception {
        return api().postJson(session, "/api/chat/messages", mapper.writeValueAsString(
                mapper.createObjectNode().put("pet_id", petId).put("session_id", sessionId).put("text", text)));
    }

    private long freshSession(MockHttpSession session, String petId) throws Exception {
        return api().postJson(session, "/api/chat/sessions", "{\"pet_id\":\"" + petId + "\",\"fresh\":true}").get("session_id").asLong();
    }

    private boolean inClinicQueue(MockHttpSession vet, String triageId) throws Exception {
        for (JsonNode t : api().getJson(vet, "/api/clinic/triages")) {
            if (triageId.equals(t.get("triage_id").asText())) {
                return true;
            }
        }
        return false;
    }

    // ----------------------------------------------------------------------------- H1: consentimento
    @Test
    @DisplayName("H1: 'Não quero compartilhar com a clínica' após a pergunta NÃO compartilha nada")
    void explicitRefusal_neverShares() throws Exception {
        MockHttpSession ana = api().login("ana@zelo.demo");
        String mel = api().petIdOf(ana, "Mel");
        long session = freshSession(ana, mel);
        String triageId = say(ana, mel, session, "ela teve uma convulsão").get("reply").get("triage").get("triage_id").asText();

        JsonNode reply = say(ana, mel, session, "Não quero compartilhar com a clínica");

        assertThat(reply.has("shared_triage_id")).isFalse();
        assertThat(reply.get("reply").get("text").asText()).contains("não compartilhei nada");
        assertThat(inClinicQueue(api().login("vet@zelo.demo"), triageId)).isFalse();
    }

    @Test
    @DisplayName("H1: relato que começa com 'pode/sim' não vira consentimento e ainda é triado")
    void symptomReportStartingWithYesWord_isTriagedNotConsent() throws Exception {
        MockHttpSession ana = api().login("ana@zelo.demo");
        String mel = api().petIdOf(ana, "Mel");
        long session = freshSession(ana, mel);
        say(ana, mel, session, "ela teve uma convulsão");

        JsonNode reply = say(ana, mel, session, "Pode ser grave? Ela está vomitando sangue");

        assertThat(reply.has("shared_triage_id")).isFalse();
        assertThat(reply.get("reply").get("triage").get("symptoms")).extracting(JsonNode::asText).contains("vomito_sangue");
    }

    @Test
    @DisplayName("H1: perguntar 'posso compartilhar a conversa com minha esposa?' não concede nada")
    void shareQuestion_isNotConsent() throws Exception {
        MockHttpSession ana = api().login("ana@zelo.demo");
        String mel = api().petIdOf(ana, "Mel");
        long session = freshSession(ana, mel);
        String triageId = say(ana, mel, session, "ela teve uma convulsão").get("reply").get("triage").get("triage_id").asText();
        say(ana, mel, session, "agora não");

        JsonNode reply = say(ana, mel, session, "posso compartilhar a conversa com minha esposa?");

        assertThat(reply.has("shared_triage_id")).isFalse();
        assertThat(inClinicQueue(api().login("vet@zelo.demo"), triageId)).isFalse();
    }

    // ------------------------------------------------------------------- M3: consentimento revogado
    @Test
    @DisplayName("M3: revogar e conceder de novo NÃO reexibe triagens antigas à clínica")
    void regrantingConsent_doesNotResurrectOldTriages() throws Exception {
        MockHttpSession ana = api().login("ana@zelo.demo");
        String thor = api().petIdOf(ana, "Thor");
        long session = freshSession(ana, thor);
        String triageId = say(ana, thor, session, "ele não consegue fazer xixi").get("reply").get("triage").get("triage_id").asText();
        say(ana, thor, session, "sim");
        MockHttpSession vet = api().login("vet@zelo.demo");
        assertThat(inClinicQueue(vet, triageId)).isTrue();

        long consentId = api().getJson(ana, "/api/sharing/consents?pet_id=" + thor).get(0).get("id").asLong();
        mvc.perform(delete("/api/sharing/consents/" + consentId).session(ana).with(csrf())).andExpect(status().isNoContent());
        assertThat(inClinicQueue(vet, triageId)).isFalse();

        api().postJson(ana, "/api/sharing/consents", "{\"pet_id\":\"" + thor + "\"}");

        assertThat(inClinicQueue(vet, triageId)).isFalse();
    }

    // ---------------------------------------------------------------- M4: minimização para a clínica
    @Test
    @DisplayName("M4: anotações livres do tutor não aparecem no histórico enviado à clínica; a procedência vem junto")
    void clinicTimeline_excludesFreeTextNotes() throws Exception {
        MockHttpSession ana = api().login("ana@zelo.demo");
        String mel = api().petIdOf(ana, "Mel");
        api().postJson(ana, "/api/pets/" + mel + "/events",
                "{\"event_type\":\"note\",\"title\":\"Anotação íntima da família\",\"text\":\"texto privado\"}");
        api().postJson(ana, "/api/pets/" + mel + "/events",
                "{\"event_type\":\"medication\",\"title\":\"Remédio de rotina\",\"text\":\"informado pelo tutor\"}");
        long session = freshSession(ana, mel);
        String triageId = say(ana, mel, session, "ela teve uma convulsão").get("reply").get("triage").get("triage_id").asText();
        say(ana, mel, session, "sim");

        JsonNode detail = api().getJson(api().login("vet@zelo.demo"), "/api/clinic/triages/" + triageId);

        assertThat(detail.get("timeline")).extracting(e -> e.get("title").asText())
                .doesNotContain("Anotação íntima da família").contains("Remédio de rotina");
        assertThat(detail.get("timeline")).extracting(e -> e.get("source").asText()).contains("tutor");
        assertThat(api().getJson(ana, "/api/pets/" + mel + "/timeline?limit=50")).extracting(e -> e.get("title").asText())
                .contains("Anotação íntima da família");
    }

    @Test
    @DisplayName("M4: ao recarregar a conversa, o cartão da triagem compartilhada aparece como compartilhado")
    void chatHistory_reflectsCurrentSharedState() throws Exception {
        MockHttpSession ana = api().login("ana@zelo.demo");
        String mel = api().petIdOf(ana, "Mel");
        long session = freshSession(ana, mel);
        say(ana, mel, session, "ela teve uma convulsão");
        say(ana, mel, session, "sim");

        JsonNode card = null;
        for (JsonNode message : api().getJson(ana, "/api/chat/sessions/" + session + "/messages").get("messages")) {
            if (message.hasNonNull("triage")) {
                card = message.get("triage");
            }
        }

        assertThat(card).isNotNull();
        assertThat(card.get("shared_with_clinic").asBoolean()).isTrue();
    }

    // --------------------------------------------------------------------------------- Swagger
    @Test
    @DisplayName("docs da API exigem autenticação (não expõem a superfície da API a anônimos)")
    void apiDocs_requireAuthentication() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().is4xxClientError());

        mvc.perform(get("/v3/api-docs").session(api().login("ana@zelo.demo"))).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------- toString sem segredos
    @Test
    @DisplayName("segredos não aparecem no toString (hash de senha e chave de API)")
    void secrets_areNotInToString() {
        var principal = new br.com.zelo.security.AppUserDetails(1L, "a@b.c", "Ana", br.com.zelo.domain.Role.TUTOR, "$2a$10$hashsecreto");
        var llm = new br.com.zelo.config.ZeloProperties.Llm("auto", "sk-ant-segredo", "m", "http://x", 5);

        assertThat(principal.toString()).doesNotContain("hashsecreto");
        assertThat(llm.toString()).doesNotContain("sk-ant-segredo");
    }

    @Test
    @DisplayName("usuário simulado sem perfil não acessa a API do tutor")
    void unauthenticatedMockUserWithoutRole_isForbidden() throws Exception {
        mvc.perform(get("/api/pets").with(user("x").roles("OUTRO"))).andExpect(status().isForbidden());
        assertThat(URI.create("/api/%74riage").getPath()).isEqualTo("/api/triage"); // premissa do teste do limitador
        mvc.perform(post("/api/triage").with(csrf()).contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
    }
}
