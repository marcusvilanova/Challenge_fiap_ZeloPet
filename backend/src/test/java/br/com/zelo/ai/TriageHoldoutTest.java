package br.com.zelo.ai;

import br.com.zelo.ai.GoldSetEvaluator.Metrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Conjuntos INDEPENDENTES do de desenvolvimento, para medir generalização:
 * <ul>
 *   <li>holdout — 56 relatos (rodada 1 = antes de qualquer ajuste; hoje já usado no desenvolvimento);</li>
 *   <li>blind — 36 relatos escritos depois, avaliados uma única vez antes de qualquer ajuste.</li>
 * </ul>
 * Os relatórios vão para target/triage-eval-*.md; a análise completa está em docs/validacao-e-testes.md.
 */
class TriageHoldoutTest {

    @Test
    @DisplayName("holdout: gera o relatório de generalização")
    void holdout_reportsGeneralization() throws IOException {
        Metrics metrics = evaluateAndWrite("/eval/holdout_cases.jsonl", "triage-eval-holdout.md");

        assertThat(metrics.total()).isGreaterThanOrEqualTo(50);
    }

    @Test
    @DisplayName("blind: gera o relatório do conjunto cego")
    void blind_reportsGeneralization() throws IOException {
        Metrics metrics = evaluateAndWrite("/eval/blind_cases.jsonl", "triage-eval-blind.md");

        assertThat(metrics.total()).isGreaterThanOrEqualTo(30);
    }

    private static Metrics evaluateAndWrite(String resource, String reportName) throws IOException {
        Metrics metrics = GoldSetEvaluator.evaluate(GoldSetEvaluator.loadCases(resource));
        Path report = Path.of("target", reportName);
        Files.createDirectories(report.getParent());
        Files.writeString(report, GoldSetEvaluator.markdown(metrics), StandardCharsets.UTF_8);
        return metrics;
    }
}
