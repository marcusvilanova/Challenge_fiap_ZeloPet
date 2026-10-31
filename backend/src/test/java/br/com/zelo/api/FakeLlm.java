package br.com.zelo.api;

import br.com.zelo.ai.PetContext;
import br.com.zelo.ai.TriageAnalysis;
import br.com.zelo.llm.LlmClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/** LLM de teste com comportamento configurável (estático para ser reiniciado a cada teste). */
final class FakeLlm implements LlmClient {

    static volatile List<String> extraction = List.of();
    static volatile Optional<String> rewrite = Optional.empty();
    static final AtomicInteger EXTRACTION_CALLS = new AtomicInteger();
    static final AtomicInteger REWRITE_CALLS = new AtomicInteger();

    static void reset() {
        extraction = List.of();
        rewrite = Optional.empty();
        EXTRACTION_CALLS.set(0);
        REWRITE_CALLS.set(0);
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public String label() {
        return "fake-llm-1";
    }

    @Override
    public List<String> extractSymptoms(String userText, PetContext pet, Map<String, String> allowedIdToLabel) {
        EXTRACTION_CALLS.incrementAndGet();
        return extraction;
    }

    @Override
    public Optional<String> rewriteReply(String userText, PetContext pet, TriageAnalysis analysis, String draft) {
        REWRITE_CALLS.incrementAndGet();
        return rewrite;
    }
}
