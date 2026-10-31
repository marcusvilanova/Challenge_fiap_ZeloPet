package br.com.zelo.llm;

import br.com.zelo.ai.PetContext;
import br.com.zelo.ai.TriageAnalysis;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Usado quando não há chave de API: todo o fluxo funciona só com o motor local (regras + léxico). */
public final class NoopLlmClient implements LlmClient {

    public static final String LABEL = "local-fallback";

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public String label() {
        return LABEL;
    }

    @Override
    public List<String> extractSymptoms(String userText, PetContext pet, Map<String, String> allowedIdToLabel) {
        return List.of();
    }

    @Override
    public Optional<String> rewriteReply(String userText, PetContext pet, TriageAnalysis analysis, String draft) {
        return Optional.empty();
    }
}
