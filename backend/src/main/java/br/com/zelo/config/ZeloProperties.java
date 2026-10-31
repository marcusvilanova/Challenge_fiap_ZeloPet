package br.com.zelo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuração tipada (prefixo {@code zelo}). Segredos chegam por variável de ambiente. */
@ConfigurationProperties(prefix = "zelo")
public record ZeloProperties(Seed seed, Llm llm, Vision vision, RateLimit rateLimit) {

    public record Seed(boolean enabled, String demoPassword) {
    }

    public record Llm(String provider, String apiKey, String model, String baseUrl, int timeoutSeconds) {

        public boolean enabled() {
            boolean forcedLocal = "local".equalsIgnoreCase(provider);
            return !forcedLocal && apiKey != null && !apiKey.isBlank();
        }

        /** Nunca inclui a chave de API (evita vazar em logs). */
        @Override
        public String toString() {
            return "Llm[provider=" + provider + ", model=" + model + ", apiKey=" + (enabled() ? "***" : "(ausente)") + "]";
        }
    }

    public record Vision(String url, int timeoutSeconds) {
    }

    public record RateLimit(int perMinute, int loginPerMinute) {
    }
}
