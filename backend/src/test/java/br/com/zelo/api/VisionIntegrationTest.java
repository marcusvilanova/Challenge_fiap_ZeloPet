package br.com.zelo.api;

import br.com.zelo.repo.ImageAnalysisRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fluxo de análise de foto contra um serviço de visão simulado (contrato do POST /classify do serviço Python). */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:zelo-vision-${random.uuid};DB_CLOSE_DELAY=-1",
        "zelo.seed.enabled=true",
        "zelo.seed.demo-password=" + ApiTestSupport.DEMO_PASSWORD,
        "zelo.llm.provider=local",
        "zelo.rate-limit.per-minute=1000", "zelo.rate-limit.login-per-minute=1000"})
@AutoConfigureMockMvc
class VisionIntegrationTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};
    private static final String RINGWORM = classification("ringworm", "Tinha (dermatofitose)", 0.91, false);
    private static final String HEALTHY = classification("Healthy", "Pele saudável", 0.88, false);
    private static final String LOW = classification("Dermatitis", "Dermatite", 0.34, true);

    private static HttpServer visionStub;
    private static volatile String nextResponse = RINGWORM;
    private static volatile int nextStatus = 200;

    @BeforeAll
    static void startVisionStub() throws IOException {
        visionStub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        visionStub.createContext("/classify", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] body = nextResponse.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(nextStatus, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        visionStub.createContext("/health", exchange -> {
            byte[] body = "{\"status\":\"ok\",\"model_version\":\"zelo-skin-stub\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        visionStub.start();
    }

    @AfterAll
    static void stopVisionStub() {
        visionStub.stop(0);
    }

    @DynamicPropertySource
    static void visionUrl(DynamicPropertyRegistry registry) throws IOException {
        // O stub sobe antes do contexto Spring para a URL já estar disponível.
        if (visionStub == null) {
            startVisionStub();
        }
        registry.add("zelo.vision.url", () -> "http://127.0.0.1:" + visionStub.getAddress().getPort());
    }

    private static String classification(String label, String labelPt, double probability, boolean low) {
        return "{\"model_version\":\"zelo-skin-stub\",\"predictions\":[{\"label\":\"" + label + "\",\"label_pt\":\"" + labelPt
                + "\",\"probability\":" + probability + "},{\"label\":\"Healthy\",\"label_pt\":\"Pele saudável\",\"probability\":0.05}],"
                + "\"low_confidence\":" + low + ",\"threshold\":0.5}";
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private ImageAnalysisRepository analyses;

    private ApiTestSupport api;
    private MockHttpSession ana;

    @BeforeEach
    void setUp() throws Exception {
        nextResponse = RINGWORM;
        nextStatus = 200;
        api = new ApiTestSupport(mvc, mapper);
        ana = api.login("ana@zelo.demo");
    }

    private MvcResult upload(MockHttpSession session, String petId, byte[] bytes, Long sessionId, int expected) throws Exception {
        var request = multipart("/api/vision/analyze").file(new MockMultipartFile("file", "foto.jpg", "image/jpeg", bytes))
                .param("pet_id", petId).session(session).with(csrf());
        if (sessionId != null) {
            request.param("session_id", String.valueOf(sessionId));
        }
        return mvc.perform(request).andExpect(status().is(expected)).andReturn();
    }

    @Test
    @DisplayName("cão: devolve classificação experimental, só guarda o hash e registra na linha do tempo e na conversa")
    void dog_getsExperimentalResult() throws Exception {
        String mel = api.petIdOf(ana, "Mel");
        long sessionId = api.postJson(ana, "/api/chat/sessions", "{\"pet_id\":\"" + mel + "\",\"fresh\":true}").get("session_id").asLong();
        long before = analyses.count();

        JsonNode result = mapper.readTree(upload(ana, mel, JPEG, sessionId, 200).getResponse().getContentAsString());

        assertThat(result.get("top_label").asText()).isEqualTo("ringworm");
        assertThat(result.get("top_label_pt").asText()).isEqualTo("Tinha (dermatofitose)");
        assertThat(result.get("experimental").asBoolean()).isTrue();
        assertThat(result.get("priority").asText()).isEqualTo("media");
        assertThat(result.get("next_action").asText()).isEqualTo("agendar_consulta");
        assertThat(result.get("human_review_required").asBoolean()).isTrue();
        assertThat(result.get("disclaimer").asText()).contains("Não é diagnóstico");
        assertThat(analyses.count()).isEqualTo(before + 1);
        assertThat(analyses.findAll().get(analyses.findAll().size() - 1).getSha256()).hasSize(64);
        assertThat(api.getJson(ana, "/api/pets/" + mel + "/timeline")).extracting(e -> e.get("type").asText()).contains("vision");
        JsonNode history = api.getJson(ana, "/api/chat/sessions/" + sessionId + "/messages");
        assertThat(history.get("messages").get(history.get("messages").size() - 1).get("vision").get("top_label").asText()).isEqualTo("ringworm");
    }

    @Test
    @DisplayName("pele saudável com confiança alta vira prioridade baixa; confiança baixa nunca é tratada como saudável")
    void healthyAndLowConfidence() throws Exception {
        String mel = api.petIdOf(ana, "Mel");

        nextResponse = HEALTHY;
        assertThat(mapper.readTree(upload(ana, mel, JPEG, null, 200).getResponse().getContentAsString()).get("priority").asText())
                .isEqualTo("baixa");

        nextResponse = LOW;
        JsonNode low = mapper.readTree(upload(ana, mel, JPEG, null, 200).getResponse().getContentAsString());
        assertThat(low.get("low_confidence").asBoolean()).isTrue();
        assertThat(low.get("priority").asText()).isEqualTo("media");
        assertThat(low.get("message").asText()).contains("confiança ficou baixa");
    }

    @Test
    @DisplayName("gato: recusa com explicação (o classificador só foi treinado com cães)")
    void cat_isRefused() throws Exception {
        String thor = api.petIdOf(ana, "Thor");

        mvc.perform(multipart("/api/vision/analyze").file(new MockMultipartFile("file", "f.jpg", "image/jpeg", JPEG))
                        .param("pet_id", thor).session(ana).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("apenas com cães")));
    }

    @Test
    @DisplayName("arquivo que não é imagem (mesmo com nome .jpg) e arquivo vazio são recusados")
    void invalidFiles_areRejected() throws Exception {
        String mel = api.petIdOf(ana, "Mel");

        upload(ana, mel, "isto é texto, não uma imagem".getBytes(StandardCharsets.UTF_8), null, 400);
        upload(ana, mel, new byte[0], null, 400);
    }

    @Test
    @DisplayName("serviço de visão fora do ar: 503 com mensagem amigável (o chat segue funcionando)")
    void visionDown_returns503() throws Exception {
        String mel = api.petIdOf(ana, "Mel");
        nextStatus = 500;
        nextResponse = "{}";

        mvc.perform(multipart("/api/vision/analyze").file(new MockMultipartFile("file", "f.jpg", "image/jpeg", JPEG))
                        .param("pet_id", mel).session(ana).with(csrf()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("indisponível")));
    }

    @Test
    @DisplayName("outro tutor não analisa foto de pet alheio (404)")
    void otherTutor_getsNotFound() throws Exception {
        String mel = api.petIdOf(ana, "Mel");

        upload(api.login("bruno@zelo.demo"), mel, JPEG, null, 404);
    }

    @Test
    @DisplayName("status do sistema enxerga o serviço de visão")
    void status_reportsVision() throws Exception {
        JsonNode status = api.getJson(ana, "/api/system/status");

        assertThat(status.get("vision_available").asBoolean()).isTrue();
        assertThat(status.get("vision_model").asText()).isEqualTo("zelo-skin-stub");
    }
}
