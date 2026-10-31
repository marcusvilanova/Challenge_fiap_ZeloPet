package br.com.zelo.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:zelo-it-${random.uuid};DB_CLOSE_DELAY=-1",
        "zelo.seed.enabled=true",
        "zelo.seed.demo-password=" + ApiTestSupport.DEMO_PASSWORD,
        "zelo.llm.provider=local",
        "zelo.rate-limit.per-minute=1000", "zelo.rate-limit.login-per-minute=1000",
        "zelo.vision.url=http://localhost:1"})
@AutoConfigureMockMvc
class ChatApiIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;

    private ApiTestSupport api;

    @BeforeEach
    void setUp() {
        api = new ApiTestSupport(mvc, mapper);
    }

    private String chatBody(String petId, Long sessionId, String text) throws Exception {
        var node = mapper.createObjectNode().put("pet_id", petId).put("text", text);
        if (sessionId != null) {
            node.put("session_id", sessionId);
        }
        return mapper.writeValueAsString(node);
    }

    private java.util.List<String> queueIds(MockHttpSession vet) throws Exception {
        java.util.List<String> ids = new java.util.ArrayList<>();
        api.getJson(vet, "/api/clinic/triages").forEach(t -> ids.add(t.get("triage_id").asText()));
        return ids;
    }

    private JsonNode queueEntry(MockHttpSession vet, String triageId) throws Exception {
        for (JsonNode t : api.getJson(vet, "/api/clinic/triages")) {
            if (triageId.equals(t.get("triage_id").asText())) {
                return t;
            }
        }
        throw new AssertionError("triagem ausente da fila da clínica: " + triageId);
    }

    // ---------------------------------------------------------------------------- autenticação
    @Test
    @DisplayName("login: credenciais erradas → 401 com mensagem genérica; corretas → 200 com o perfil")
    void login_wrongAndRightCredentials() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).param("email", "ana@zelo.demo").param("password", "errada"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos"));

        mvc.perform(post("/api/auth/login").with(csrf()).param("email", "ana@zelo.demo")
                        .param("password", ApiTestSupport.DEMO_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("TUTOR"))
                .andExpect(jsonPath("$.email").value("ana@zelo.demo"));
    }

    @Test
    @DisplayName("sem CSRF, requisições que alteram estado são recusadas (403), inclusive o login")
    void mutatingRequests_withoutCsrf_areRejected() throws Exception {
        mvc.perform(post("/api/auth/login").param("email", "ana@zelo.demo").param("password", ApiTestSupport.DEMO_PASSWORD))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("rotas protegidas: anônimo → 401; tutor na rota da clínica → 403; clínica na rota do tutor → 403")
    void routes_areProtectedByProfile() throws Exception {
        mvc.perform(get("/api/pets")).andExpect(status().isUnauthorized());

        MockHttpSession tutor = api.login("ana@zelo.demo");
        mvc.perform(get("/api/clinic/triages").session(tutor)).andExpect(status().isForbidden());

        MockHttpSession clinic = api.login("vet@zelo.demo");
        mvc.perform(get("/api/pets").session(clinic)).andExpect(status().isForbidden());
        mvc.perform(get("/api/clinic/triages").session(clinic)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("páginas: /chat.html exige tutor e /clinic.html exige clínica (anônimo é redirecionado)")
    void pages_areProtectedByProfile() throws Exception {
        mvc.perform(get("/chat.html")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/clinic.html").session(api.login("ana@zelo.demo"))).andExpect(status().is3xxRedirection());
        mvc.perform(get("/index.html")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("cabeçalhos de segurança: CSP restritiva, nosniff e frame deny")
    void securityHeaders_arePresent() throws Exception {
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Security-Policy", org.hamcrest.Matchers.containsString("script-src 'self'")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("X-Content-Type-Options", "nosniff"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("X-Frame-Options", "DENY"));
    }

    // ------------------------------------------------------------------- fluxo tutor → clínica
    @Test
    @DisplayName("fluxo completo: relato → triagem estruturada → compartilha → clínica revisa → tutor vê na linha do tempo")
    void fullFlow_tutorToClinicAndBack() throws Exception {
        MockHttpSession ana = api.login("ana@zelo.demo");
        String mel = api.petIdOf(ana, "Mel");

        JsonNode opened = api.postJson(ana, "/api/chat/sessions", "{\"pet_id\":\"" + mel + "\"}");
        long sessionId = opened.get("session_id").asLong();
        assertThat(opened.get("messages").get(0).get("text").asText()).contains("Mel").contains("Zelo");

        JsonNode reply = api.postJson(ana, "/api/chat/messages", chatBody(mel, sessionId, "Ela teve uma convulsão agora há pouco"));
        JsonNode triage = reply.get("reply").get("triage");
        assertThat(triage.get("priority").asText()).isEqualTo("emergencia");
        assertThat(triage.get("next_action").asText()).isEqualTo("buscar_atendimento_urgente");
        assertThat(triage.get("symptoms")).extracting(JsonNode::asText).contains("convulsao");
        assertThat(triage.get("human_review_required").asBoolean()).isTrue();
        assertThat(triage.get("model_version").asText()).isEqualTo("zelo-triage-hybrid-1.0");
        assertThat(triage.get("disclaimer").asText()).contains("não substitui avaliação veterinária");
        assertThat(triage.get("engine").get("llm").asText()).isEqualTo("local-fallback");
        assertThat(triage.get("shared_with_clinic").asBoolean()).isFalse();
        String triageId = triage.get("triage_id").asText();

        MockHttpSession vet = api.login("vet@zelo.demo");
        assertThat(queueIds(vet)).doesNotContain(triageId); // nada compartilhado ainda
        mvc.perform(get("/api/clinic/triages/" + triageId).session(vet)).andExpect(status().isNotFound());

        JsonNode shared = api.postJson(ana, "/api/chat/messages", chatBody(mel, sessionId, "Sim, compartilhar com a clínica"));
        assertThat(shared.get("shared_triage_id").asText()).isEqualTo(triageId);

        JsonNode entry = queueEntry(vet, triageId);
        assertThat(entry.get("priority").asText()).isEqualTo("emergencia");
        assertThat(entry.get("reviewed").asBoolean()).isFalse();

        JsonNode detail = api.getJson(vet, "/api/clinic/triages/" + triageId);
        assertThat(detail.get("pet").get("name").asText()).isEqualTo("Mel");
        assertThat(detail.get("input_text").asText()).contains("convulsão");

        String review = "{\"triage_id\":\"" + triageId + "\",\"decision\":\"CONFIRMADA\",\"final_priority\":\"emergencia\","
                + "\"note\":\"Tutor orientado a vir imediatamente.\"}";
        api.postJson(vet, "/api/clinic/reviews", review);
        assertThat(queueEntry(vet, triageId).get("reviewed").asBoolean()).isTrue();

        JsonNode timeline = api.getJson(ana, "/api/pets/" + mel + "/timeline");
        assertThat(timeline).extracting(e -> e.get("type").asText()).contains("triage", "consent", "share", "review");
    }

    @Test
    @DisplayName("revisão: ajustar exige prioridade diferente da sugerida; confirmar exige a mesma")
    void review_validatesDecisionAgainstPriority() throws Exception {
        MockHttpSession ana = api.login("ana@zelo.demo");
        String mel = api.petIdOf(ana, "Mel");
        String triageId = api.postJson(ana, "/api/triage", "{\"pet_id\":\"" + mel + "\",\"text\":\"ela está com tosse\"}")
                .get("triage_id").asText();
        api.postJson(ana, "/api/sharing/consents", "{\"pet_id\":\"" + mel + "\"}");
        api.postJson(ana, "/api/chat/sessions", "{\"pet_id\":\"" + mel + "\"}");
        // compartilha a triagem avulsa via chat não é possível; usa o fluxo do chat para criar uma compartilhada
        long sessionId = api.postJson(ana, "/api/chat/sessions", "{\"pet_id\":\"" + mel + "\",\"fresh\":true}").get("session_id").asLong();
        api.postJson(ana, "/api/chat/messages", chatBody(mel, sessionId, "ela teve uma convulsão"));
        String shared = api.postJson(ana, "/api/chat/messages", chatBody(mel, sessionId, "sim")).get("shared_triage_id").asText();

        MockHttpSession vet = api.login("vet@zelo.demo");
        mvc.perform(post("/api/clinic/reviews").session(vet).with(csrf()).contentType("application/json")
                        .content("{\"triage_id\":\"" + shared + "\",\"decision\":\"AJUSTADA\",\"final_priority\":\"emergencia\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/clinic/reviews").session(vet).with(csrf()).contentType("application/json")
                        .content("{\"triage_id\":\"" + shared + "\",\"decision\":\"CONFIRMADA\",\"final_priority\":\"alta\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/clinic/reviews").session(vet).with(csrf()).contentType("application/json")
                        .content("{\"triage_id\":\"" + triageId + "\",\"decision\":\"CONFIRMADA\",\"final_priority\":\"media\"}"))
                .andExpect(status().isNotFound()); // triagem avulsa não compartilhada
    }

    @Test
    @DisplayName("revogar o consentimento oculta a triagem do painel da clínica")
    void revokingConsent_hidesTriageFromClinic() throws Exception {
        MockHttpSession ana = api.login("ana@zelo.demo");
        String thor = api.petIdOf(ana, "Thor");
        long sessionId = api.postJson(ana, "/api/chat/sessions", "{\"pet_id\":\"" + thor + "\"}").get("session_id").asLong();
        api.postJson(ana, "/api/chat/messages", chatBody(thor, sessionId, "o gato não consegue fazer xixi"));
        api.postJson(ana, "/api/chat/messages", chatBody(thor, sessionId, "sim"));

        MockHttpSession vet = api.login("vet@zelo.demo");
        assertThat(api.getJson(vet, "/api/clinic/triages")).extracting(t -> t.get("pet_name").asText()).contains("Thor");

        JsonNode consents = api.getJson(ana, "/api/sharing/consents?pet_id=" + thor);
        long consentId = consents.get(0).get("id").asLong();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/sharing/consents/" + consentId).session(ana).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(api.getJson(vet, "/api/clinic/triages")).extracting(t -> t.get("pet_name").asText()).doesNotContain("Thor");
    }

    // ---------------------------------------------------------------------------- isolamento
    @Test
    @DisplayName("isolamento: um tutor não acessa pet, conversa nem consentimentos de outro (404)")
    void tutors_cannotAccessEachOthersData() throws Exception {
        MockHttpSession ana = api.login("ana@zelo.demo");
        MockHttpSession bruno = api.login("bruno@zelo.demo");
        String mel = api.petIdOf(ana, "Mel");
        long anaSession = api.postJson(ana, "/api/chat/sessions", "{\"pet_id\":\"" + mel + "\"}").get("session_id").asLong();

        mvc.perform(get("/api/pets/" + mel).session(bruno)).andExpect(status().isNotFound());
        mvc.perform(get("/api/pets/" + mel + "/timeline").session(bruno)).andExpect(status().isNotFound());
        mvc.perform(get("/api/pets/" + mel + "/care-plan").session(bruno)).andExpect(status().isNotFound());
        mvc.perform(get("/api/chat/sessions/" + anaSession + "/messages").session(bruno)).andExpect(status().isNotFound());
        mvc.perform(post("/api/chat/messages").session(bruno).with(csrf()).contentType("application/json")
                        .content(chatBody(mel, anaSession, "oi"))).andExpect(status().isNotFound());
        mvc.perform(post("/api/sharing/consents").session(bruno).with(csrf()).contentType("application/json")
                        .content("{\"pet_id\":\"" + mel + "\"}")).andExpect(status().isNotFound());
        mvc.perform(get("/api/pets/PET-9999").session(bruno)).andExpect(status().isNotFound());
        mvc.perform(get("/api/pets/abc").session(bruno)).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------------------- contratos
    @Test
    @DisplayName("POST /api/triage devolve a saída estruturada do contrato (Sprint 3) e persiste")
    void triageEndpoint_returnsStructuredContract() throws Exception {
        MockHttpSession ana = api.login("ana@zelo.demo");
        String mel = api.petIdOf(ana, "Mel");

        JsonNode result = api.postJson(ana, "/api/triage",
                "{\"pet_id\":\"" + mel + "\",\"text\":\"Meu cão está abatido e vomitou duas vezes\"}");

        assertThat(result.get("pet_id").asText()).isEqualTo(mel);
        assertThat(result.get("priority").asText()).isEqualTo("alta");
        assertThat(result.get("next_action").asText()).isEqualTo("contatar_clinica_veterinaria");
        assertThat(result.get("symptoms")).extracting(JsonNode::asText).containsExactlyInAnyOrder("apatia", "vomito");
        assertThat(result.get("confidence").asDouble()).isBetween(0.5, 1.0);
        assertThat(result.get("human_review_required").asBoolean()).isTrue();
        assertThat(result.get("triage_id").asText()).startsWith("TRI-");
        assertThat(api.getJson(ana, "/api/pets/" + mel + "/timeline")).extracting(e -> e.get("type").asText()).contains("triage");
    }

    @Test
    @DisplayName("entrada inválida: texto vazio, longo demais ou JSON malformado → 400 sem vazar detalhes")
    void invalidInput_isRejectedSafely() throws Exception {
        MockHttpSession ana = api.login("ana@zelo.demo");
        String mel = api.petIdOf(ana, "Mel");

        mvc.perform(post("/api/chat/messages").session(ana).with(csrf()).contentType("application/json")
                        .content(chatBody(mel, null, "   "))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/chat/messages").session(ana).with(csrf()).contentType("application/json")
                        .content(chatBody(mel, null, "a".repeat(1001)))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.text").exists());
        mvc.perform(post("/api/chat/messages").session(ana).with(csrf()).contentType("application/json")
                        .content("{nao é json")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Requisição inválida"));
    }

    @Test
    @DisplayName("cadastro de pet valida espécie, histórico e peso; e a lista reflete o novo pet")
    void createPet_validatesAndLists() throws Exception {
        MockHttpSession bruno = api.login("bruno@zelo.demo");
        mvc.perform(post("/api/pets").session(bruno).with(csrf()).contentType("application/json")
                .content("{\"name\":\"Piu\",\"species\":\"passaro\",\"female\":true}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/pets").session(bruno).with(csrf()).contentType("application/json")
                .content("{\"name\":\"Piu\",\"species\":\"gato\",\"female\":true,\"history_tags\":[\"inventado\"]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/pets").session(bruno).with(csrf()).contentType("application/json")
                .content("{\"name\":\"Piu\",\"species\":\"gato\",\"female\":true,\"weight_kg\":500}")).andExpect(status().isBadRequest());

        JsonNode created = api.postJson(bruno, "/api/pets",
                "{\"name\":\"Piu\",\"species\":\"gato\",\"breed\":\"Persian\",\"female\":true,\"birth_date\":\"2025-01-10\","
                        + "\"weight_kg\":4.2,\"history_tags\":[\"vacinado\"]}");
        assertThat(created.get("brachycephalic").asBoolean()).isTrue();
        assertThat(api.getJson(bruno, "/api/pets")).extracting(p -> p.get("name").asText()).contains("Piu", "Rex");
    }

    @Test
    @DisplayName("cadastro de tutor: valida e-mail/senha e não revela se o e-mail já existe em detalhes")
    void register_validatesAndRejectsDuplicates() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json")
                .content("{\"email\":\"novo@teste.com\",\"name\":\"Novo\",\"password\":\"curta\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json")
                .content("{\"email\":\"novo@teste.com\",\"name\":\"Novo\",\"password\":\"senha-segura-123\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("TUTOR"));
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json")
                .content("{\"email\":\"NOVO@teste.com\",\"name\":\"Novo\",\"password\":\"senha-segura-123\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("histórico: reabrir a conversa devolve as mensagens anteriores, inclusive o cartão de triagem")
    void history_survivesReopening() throws Exception {
        MockHttpSession ana = api.login("ana@zelo.demo");
        String mel = api.petIdOf(ana, "Mel");
        long sessionId = api.postJson(ana, "/api/chat/sessions", "{\"pet_id\":\"" + mel + "\",\"fresh\":true}").get("session_id").asLong();
        api.postJson(ana, "/api/chat/messages", chatBody(mel, sessionId, "ela teve uma convulsão"));

        JsonNode reopened = api.postJson(ana, "/api/chat/sessions", "{\"pet_id\":\"" + mel + "\"}");

        assertThat(reopened.get("session_id").asLong()).isEqualTo(sessionId);
        JsonNode messages = reopened.get("messages");
        assertThat(messages.get(messages.size() - 1).get("triage").get("priority").asText()).isEqualTo("emergencia");
        assertThat(messages.get(messages.size() - 2).get("author").asText()).isEqualTo("USER");
    }

    @Test
    @DisplayName("plano de cuidados e status do sistema respondem para tutor autenticado")
    void carePlanAndStatus() throws Exception {
        MockHttpSession ana = api.login("ana@zelo.demo");
        String mel = api.petIdOf(ana, "Mel");

        JsonNode plan = api.getJson(ana, "/api/pets/" + mel + "/care-plan");
        assertThat(plan).extracting(i -> i.get("status").asText()).contains("OVERDUE", "DUE_SOON", "OK");

        JsonNode status = api.getJson(ana, "/api/system/status");
        assertThat(status.get("llm_enabled").asBoolean()).isFalse();
        assertThat(status.get("llm").asText()).isEqualTo("local-fallback");
        assertThat(status.get("vision_available").asBoolean()).isFalse();
        assertThat(status.get("rules_version").asText()).isEqualTo("zelo-rules-1.0");
    }
}
