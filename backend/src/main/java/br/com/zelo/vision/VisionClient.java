package br.com.zelo.vision;

import br.com.zelo.config.ZeloProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Cliente HTTP do serviço Python de visão (ia/vision/service). Falhas viram {@link Optional#empty()}/exceção tratada. */
@Component
public class VisionClient {

    private static final Logger LOG = LoggerFactory.getLogger(VisionClient.class);
    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(2);

    /** Resposta do POST /classify do serviço Python (JSON em snake_case). */
    public record Classification(String modelVersion, List<Prediction> predictions, boolean lowConfidence, double threshold) {
    }

    public record Prediction(String label, String labelPt, double probability) {
    }

    /** Resposta do GET /health. */
    public record Health(String status, String modelVersion) {
    }

    private final RestClient rest;
    private final RestClient healthRest;

    public VisionClient(RestClient.Builder builder, ZeloProperties props) {
        JdkClientHttpRequestFactory classifyFactory = new JdkClientHttpRequestFactory();
        classifyFactory.setReadTimeout(Duration.ofSeconds(props.vision().timeoutSeconds()));
        this.rest = builder.clone().baseUrl(props.vision().url()).requestFactory(classifyFactory).build();
        JdkClientHttpRequestFactory healthFactory = new JdkClientHttpRequestFactory();
        healthFactory.setReadTimeout(HEALTH_TIMEOUT);
        this.healthRest = builder.clone().baseUrl(props.vision().url()).requestFactory(healthFactory).build();
    }

    /** @throws org.springframework.web.client.RestClientException se o serviço estiver fora do ar ou responder erro */
    public Classification classify(byte[] image, String filename) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(image) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        return rest.post().uri("/classify").contentType(MediaType.MULTIPART_FORM_DATA).body(body).retrieve()
                .body(Classification.class);
    }

    public Optional<Health> health() {
        try {
            return Optional.ofNullable(healthRest.get().uri("/health").retrieve().body(Health.class));
        } catch (RuntimeException e) {
            LOG.debug("Serviço de visão indisponível: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
