package br.com.zelo.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Utilitários de teste: login real (form + CSRF + sessão) e chamadas JSON. */
final class ApiTestSupport {

    static final String DEMO_PASSWORD = "zelo-demo-2026";

    private final MockMvc mvc;
    private final ObjectMapper mapper;

    ApiTestSupport(MockMvc mvc, ObjectMapper mapper) {
        this.mvc = mvc;
        this.mapper = mapper;
    }

    MockHttpSession login(String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").with(csrf())
                        .param("email", email).param("password", DEMO_PASSWORD))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    JsonNode getJson(MockHttpSession session, String url) throws Exception {
        MvcResult result = mvc.perform(get(url).session(session)).andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    JsonNode postJson(MockHttpSession session, String url, String body) throws Exception {
        MvcResult result = mvc.perform(post(url).session(session).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().is2xxSuccessful()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    /** Mel (cadela) é o primeiro pet da Ana, em ordem alfabética: PET-0001 no banco recém-criado. */
    String petIdOf(MockHttpSession session, String name) throws Exception {
        for (JsonNode pet : getJson(session, "/api/pets")) {
            if (name.equals(pet.get("name").asText())) {
                return pet.get("pet_id").asText();
            }
        }
        throw new IllegalStateException("pet não encontrado: " + name);
    }
}
