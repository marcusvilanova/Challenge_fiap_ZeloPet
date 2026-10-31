package br.com.zelo.ai;

import br.com.zelo.ai.GoldSetEvaluator.Case;
import br.com.zelo.ai.GoldSetEvaluator.Metrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Porta de qualidade clínica do motor: a segurança vem antes da acurácia. Emergência nunca pode
 * ser sub-triada e a sub-triagem geral é limitada; o relatório vai para target/triage-eval.md.
 */
class TriageGoldSetTest {

    private static final double MIN_ACCURACY = 0.90;
    private static final double MAX_UNDER_TRIAGE = 0.02;
    private static final double MIN_SYMPTOM_F1 = 0.90;

    @Test
    @DisplayName("conjunto-ouro: recall de emergência 100%, sub-triagem mínima e acurácia mínima")
    void goldSet_meetsSafetyAndQualityGates() throws IOException {
        List<Case> cases = GoldSetEvaluator.loadCases();
        Metrics metrics = GoldSetEvaluator.evaluate(cases);

        Path report = Path.of("target", "triage-eval.md");
        Files.createDirectories(report.getParent());
        Files.writeString(report, GoldSetEvaluator.markdown(metrics), StandardCharsets.UTF_8);

        assertThat(cases).hasSizeGreaterThanOrEqualTo(80);
        assertThat(metrics.emergencyRecall()).as("recall de emergência").isEqualTo(1.0);
        assertThat(metrics.underTriageRate()).as("sub-triagem").isLessThanOrEqualTo(MAX_UNDER_TRIAGE);
        assertThat(metrics.accuracy()).as("acurácia de prioridade").isGreaterThanOrEqualTo(MIN_ACCURACY);
        assertThat(metrics.symptomF1()).as("F1 de sintomas").isGreaterThanOrEqualTo(MIN_SYMPTOM_F1);
    }
}
