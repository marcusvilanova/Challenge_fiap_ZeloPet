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

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Dados fictícios da clínica (demo/clinic-scenarios.json) e a conversa que gerou cada triagem no detalhe. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:zelo-clinic-demo-${random.uuid};DB_CLOSE_DELAY=-1",
        "zelo.seed.enabled=true",
        "zelo.seed.demo-password=" + ApiTestSupport.DEMO_PASSWORD,
        "zelo.llm.provider=local",
        "zelo.rate-limit.per-minute=1000", "zelo.rate-limit.login-per-minute=1000",
        "zelo.vision.url=http://localhost:1"})
@AutoConfigureMockMvc
class ClinicDemoIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;

    private ApiTestSupport api() {
        return new ApiTestSupport(mvc, mapper);
    }

    private Map<String, JsonNode> queueByPet(MockHttpSession vet) throws Exception {
        Map<String, JsonNode> byPet = new HashMap<>();
        for (JsonNode item : api().getJson(vet, "/api/clinic/triages")) {
            byPet.put(item.get("pet_name").asText(), item);
        }
        return byPet;
    }

    @Test
    @DisplayName("A clínica já começa com casos fictícios compartilhados, revisados e pendentes, ao longo da semana")
    void clinicStartsWithDemoCases() throws Exception {
        MockHttpSession vet = api().login("vet@zelo.demo");
        Map<String, JsonNode> queue = queueByPet(vet);

        assertThat(queue).containsKeys("Luna", "Mia", "Bidu", "Max", "Nina", "Fred", "Simba", "Pipoca");
        assertThat(queue.get("Nina").get("priority").asText()).isEqualTo("emergencia");
        assertThat(queue.get("Nina").get("reviewed").asBoolean()).isFalse();
        assertThat(queue.get("Bidu").get("reviewed").asBoolean()).isTrue();
        assertThat(queue.get("Luna").get("tutor_name").asText()).isEqualTo("Carla Mendes");

        OffsetDateTime simba = OffsetDateTime.parse(queue.get("Simba").get("created_at").asText());
        assertThat(Duration.between(simba, OffsetDateTime.now()).toHours()).isBetween(140L, 150L);

        // a primeira da fila é a emergência pendente mais recente
        JsonNode first = api().getJson(vet, "/api/clinic/triages").get(0);
        assertThat(first.get("priority").asText()).isEqualTo("emergencia");
        assertThat(first.get("pet_name").asText()).isEqualTo("Nina");

        // revisão ajustada pela clínica fica registrada com a prioridade final escolhida
        JsonNode fred = api().getJson(vet, "/api/clinic/triages/" + queue.get("Fred").get("triage_id").asText());
        assertThat(fred.get("reviews").get(0).get("decision").asText()).isEqualTo("AJUSTADA");
        assertThat(fred.get("reviews").get(0).get("final_priority").asText()).isEqualTo("baixa");
    }

    @Test
    @DisplayName("O detalhe da triagem traz a conversa que a gerou, até a resposta com a triagem (sem o que veio depois)")
    void detailIncludesConversation() throws Exception {
        MockHttpSession vet = api().login("vet@zelo.demo");
        String maxTriage = queueByPet(vet).get("Max").get("triage_id").asText();
        JsonNode conversation = api().getJson(vet, "/api/clinic/triages/" + maxTriage).get("conversation");

        assertThat(conversation).isNotEmpty();
        assertThat(conversation.get(0).get("author").asText()).isEqualTo("BOT"); // saudação
        assertThat(conversation).extracting(m -> m.get("text").asText())
                .contains("Ele está mancando da pata traseira há uma semana")
                .doesNotContain("Sim, compartilhar com a clínica"); // a resposta de consentimento veio depois da triagem
        assertThat(conversation.get(conversation.size() - 1).get("author").asText()).isEqualTo("BOT");
    }

    @Test
    @DisplayName("Os novos tutores de demonstração entram com a senha de demonstração")
    void demoTutorsCanLogIn() throws Exception {
        MockHttpSession carla = api().login("carla@zelo.demo");
        assertThat(api().getJson(carla, "/api/pets")).extracting(p -> p.get("name").asText()).containsExactly("Luna", "Mia");
    }
}
