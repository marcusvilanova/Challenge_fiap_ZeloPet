package br.com.zelo.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** O limitador precisa rodar DE VERDADE na cadeia de filtros (login) e não ser contornado por caminho codificado. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:zelo-rate-${random.uuid};DB_CLOSE_DELAY=-1",
        "zelo.seed.enabled=true",
        "zelo.seed.demo-password=" + ApiTestSupport.DEMO_PASSWORD,
        "zelo.llm.provider=local",
        "zelo.rate-limit.per-minute=3",
        "zelo.rate-limit.login-per-minute=3",
        "zelo.vision.url=http://localhost:1"})
@AutoConfigureMockMvc
class RateLimitIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("H2: senha errada repetida → 429 a partir da 4ª tentativa (o limitador roda antes do filtro de login)")
    void badLogins_areThrottled() throws Exception {
        for (int attempt = 1; attempt <= 3; attempt++) {
            mvc.perform(post("/api/auth/login").with(csrf()).param("email", "ana@zelo.demo").param("password", "errada"))
                    .andExpect(status().isUnauthorized());
        }

        mvc.perform(post("/api/auth/login").with(csrf()).param("email", "ana@zelo.demo").param("password", "errada"))
                .andExpect(status().isTooManyRequests());
        // mesmo com a senha certa, a tentativa seguinte também é barrada (limite por endereço)
        mvc.perform(post("/api/auth/login").with(csrf()).param("email", "ana@zelo.demo").param("password", ApiTestSupport.DEMO_PASSWORD))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("M1: '/api/%74riage' conta no mesmo balde de '/api/triage' (não dá para driblar o limite codificando o caminho)")
    void encodedPath_sharesTheSameBucket() throws Exception {
        var tutor = user("ana@zelo.demo").roles("TUTOR");
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/triage").with(tutor).with(csrf()).contentType("application/json").content("{}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post(URI.create("/api/%74riage")).with(tutor).with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post(URI.create("/api/%74riage")).with(tutor).with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("cadastro de tutor também é limitado por endereço")
    void register_isThrottled() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json")
                    .content("{\"email\":\"x" + i + "@teste.com\",\"name\":\"X\",\"password\":\"senha-segura-1\"}"))
                    .andExpect(status().isCreated());
        }

        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json")
                        .content("{\"email\":\"x9@teste.com\",\"name\":\"X\",\"password\":\"senha-segura-1\"}"))
                .andExpect(status().isTooManyRequests());
    }
}
