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
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Painel do tutor: edição do pet, gestão do plano de cuidados, rotas amigáveis e CSP das páginas. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:zelo-dash-${random.uuid};DB_CLOSE_DELAY=-1",
        "zelo.seed.enabled=true",
        "zelo.seed.demo-password=" + ApiTestSupport.DEMO_PASSWORD,
        "zelo.llm.provider=local",
        "zelo.rate-limit.per-minute=1000", "zelo.rate-limit.login-per-minute=1000",
        "zelo.vision.url=http://localhost:1"})
@AutoConfigureMockMvc
class TutorDashboardIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;

    private ApiTestSupport api() {
        return new ApiTestSupport(mvc, mapper);
    }

    private String petBody(String name, String species, String weight) {
        return "{\"name\":\"" + name + "\",\"species\":\"" + species + "\",\"breed\":\"Labrador Retriever\","
                + "\"female\":true,\"birth_date\":\"2022-03-10\",\"weight_kg\":" + weight + ",\"history_tags\":[\"vacinado\"]}";
    }

    private JsonNode send(MockHttpSession session, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                          int expectedStatus) throws Exception {
        MvcResult result = mvc.perform(request.session(session).with(csrf()).contentType("application/json"))
                .andExpect(status().is(expectedStatus)).andReturn();
        String body = result.getResponse().getContentAsString();
        return body.isEmpty() ? null : mapper.readTree(body);
    }

    private boolean timelineHas(MockHttpSession session, String petId, String title) throws Exception {
        for (JsonNode event : api().getJson(session, "/api/pets/" + petId + "/timeline?limit=50")) {
            if (event.get("title").asText().equals(title)) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("Tutor edita os dados do pet; a edição vai para a linha do tempo e outro tutor recebe 404")
    void updatePet() throws Exception {
        MockHttpSession ana = api().login("ana@zelo.demo");
        String mel = api().petIdOf(ana, "Mel");

        JsonNode updated = send(ana, put("/api/pets/" + mel).content(petBody("Mel", "dog", "26.4")), 200);
        assertThat(updated.get("weight_kg").decimalValue()).isEqualByComparingTo("26.4");
        assertThat(updated.get("birth_date").asText()).isEqualTo("2022-03-10");
        assertThat(timelineHas(ana, mel, "Dados do pet atualizados")).isTrue();

        send(ana, put("/api/pets/" + mel).content(petBody("Mel", "peixe", "26.4")), 400);
        send(ana, put("/api/pets/" + mel).content(petBody("", "dog", "26.4")), 400);

        MockHttpSession bruno = api().login("bruno@zelo.demo");
        send(bruno, put("/api/pets/" + mel).content(petBody("Roubada", "dog", "1")), 404);
        assertThat(api().getJson(ana, "/api/pets/" + mel).get("name").asText()).isEqualTo("Mel");
    }

    @Test
    @DisplayName("Plano de cuidados: adicionar, marcar como feito (próxima data pelo intervalo do tipo) e remover")
    void manageCarePlan() throws Exception {
        MockHttpSession ana = api().login("ana@zelo.demo");
        String thor = api().petIdOf(ana, "Thor");
        LocalDate today = LocalDate.now();

        JsonNode added = send(ana, post("/api/pets/" + thor + "/care-plan").content(
                "{\"type\":\"VERMIFUGO\",\"name\":\"Vermífugo felino\",\"next_due\":\"" + today.plusDays(3) + "\"}"), 201);
        long itemId = added.get("id").asLong();
        assertThat(added.get("status").asText()).isEqualTo("DUE_SOON");
        assertThat(timelineHas(ana, thor, "Cuidado adicionado ao plano: Vermífugo felino")).isTrue();

        JsonNode done = send(ana, post("/api/pets/" + thor + "/care-plan/" + itemId + "/done"), 200);
        assertThat(done.get("last_done").asText()).isEqualTo(today.toString());
        assertThat(done.get("next_due").asText()).isEqualTo(today.plusDays(90).toString());
        assertThat(done.get("status").asText()).isEqualTo("OK");
        assertThat(timelineHas(ana, thor, "Cuidado realizado: Vermífugo felino")).isTrue();

        JsonNode custom = send(ana, post("/api/pets/" + thor + "/care-plan/" + itemId + "/done")
                .content("{\"next_due\":\"" + today.plusDays(10) + "\"}"), 200);
        assertThat(custom.get("next_due").asText()).isEqualTo(today.plusDays(10).toString());
        send(ana, post("/api/pets/" + thor + "/care-plan/" + itemId + "/done").content("{\"next_due\":\"" + today + "\"}"), 400);

        MockHttpSession bruno = api().login("bruno@zelo.demo");
        String rex = api().petIdOf(bruno, "Rex");
        send(bruno, post("/api/pets/" + rex + "/care-plan/" + itemId + "/done"), 404); // item é do Thor, não do Rex
        send(bruno, delete("/api/pets/" + thor + "/care-plan/" + itemId), 404);

        send(ana, delete("/api/pets/" + thor + "/care-plan/" + itemId), 204);
        for (JsonNode item : api().getJson(ana, "/api/pets/" + thor + "/care-plan")) {
            assertThat(item.get("id").asLong()).isNotEqualTo(itemId);
        }
    }

    @Test
    @DisplayName("Cuidado com tipo inválido ou datas incoerentes é recusado")
    void invalidCareItem() throws Exception {
        MockHttpSession ana = api().login("ana@zelo.demo");
        String mel = api().petIdOf(ana, "Mel");
        LocalDate today = LocalDate.now();
        send(ana, post("/api/pets/" + mel + "/care-plan").content(
                "{\"type\":\"BANHO\",\"name\":\"Banho\",\"next_due\":\"" + today.plusDays(3) + "\"}"), 400);
        send(ana, post("/api/pets/" + mel + "/care-plan").content(
                "{\"type\":\"VACINA\",\"name\":\"V10\",\"last_done\":\"" + today + "\",\"next_due\":\"" + today.minusDays(1) + "\"}"), 400);
        send(ana, post("/api/pets/" + mel + "/care-plan").content("{\"type\":\"VACINA\",\"name\":\"V10\"}"), 400);
    }

    @Test
    @DisplayName("Rotas amigáveis: /dashboard só para tutor; /apresentacao e /video são públicas com CSP própria")
    void friendlyRoutesAndCsp() throws Exception {
        mvc.perform(get("/dashboard")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/index.html"));

        MockHttpSession ana = api().login("ana@zelo.demo");
        mvc.perform(get("/dashboard").session(ana)).andExpect(status().isOk()).andExpect(forwardedUrl("/dashboard.html"));

        MockHttpSession vet = api().login("vet@zelo.demo");
        mvc.perform(get("/dashboard").session(vet)).andExpect(redirectedUrl("/index.html?acesso=negado"));

        mvc.perform(get("/apresentacao")).andExpect(status().isOk()).andExpect(forwardedUrl("/apresentacao.html"))
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("'unsafe-inline'")));
        mvc.perform(get("/video")).andExpect(status().isOk()).andExpect(forwardedUrl("/video.html"));
        mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));

        // as telas do app continuam sem script embutido; só a fonte do Google é liberada
        String appCsp = mvc.perform(get("/index.html")).andReturn().getResponse().getHeader("Content-Security-Policy");
        assertThat(appCsp).contains("script-src 'self';").doesNotContain("unsafe-inline").contains("https://fonts.googleapis.com");
    }
}
