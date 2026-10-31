package br.com.zelo.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Roda o conjunto-ouro (ia/triage/eval/gold_cases.jsonl) contra o motor e calcula as métricas. */
final class GoldSetEvaluator {

    record Case(String id, String text, PetContext pet, Priority expected, Set<String> expectedSymptoms) {
    }

    record Outcome(Case gold, Priority predicted, Set<String> predictedSymptoms) {
        boolean exact() {
            return predicted == gold.expected();
        }

        boolean underTriaged() {
            return predicted.level() < gold.expected().level();
        }

        boolean overTriaged() {
            return predicted.level() > gold.expected().level();
        }
    }

    record Metrics(int total, double accuracy, double emergencyRecall, int emergencyTotal, double underTriageRate,
                   double overTriageRate, double symptomPrecision, double symptomRecall, double symptomF1,
                   List<Outcome> outcomes) {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static List<Case> loadCases() {
        return loadCases("/eval/gold_cases.jsonl");
    }

    static List<Case> loadCases(String resource) {
        try (InputStream in = GoldSetEvaluator.class.getResourceAsStream(resource);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            List<Case> cases = new ArrayList<>();
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (!line.isBlank()) {
                    cases.add(parse(MAPPER.readTree(line)));
                }
            }
            return cases;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Case parse(JsonNode n) {
        Set<String> symptoms = new HashSet<>();
        n.get("symptoms").forEach(s -> symptoms.add(s.asText()));
        return new Case(n.get("id").asText(), n.get("text").asText(), parsePet(n.get("pet")),
                Priority.fromWire(n.get("priority").asText()), symptoms);
    }

    private static PetContext parsePet(JsonNode p) {
        if (p.isTextual()) {
            return TestEngines.pet(Species.fromString(p.asText()), 48, 15.0, false, Set.of());
        }
        Set<String> history = new HashSet<>();
        if (p.has("history")) {
            p.get("history").forEach(h -> history.add(h.asText()));
        }
        return TestEngines.pet(Species.fromString(p.get("species").asText()),
                p.has("age_months") ? p.get("age_months").asInt() : 48,
                p.has("weight_kg") ? p.get("weight_kg").asDouble() : 15.0,
                p.has("brachycephalic") && p.get("brachycephalic").asBoolean(), history);
    }

    static Metrics evaluate(List<Case> cases) {
        List<Outcome> outcomes = cases.stream().map(c -> {
            TriageAnalysis a = TestEngines.analyze(c.text(), c.pet());
            Set<String> ids = new HashSet<>();
            a.active().forEach(m -> ids.add(m.id()));
            return new Outcome(c, a.priority(), ids);
        }).toList();

        long exact = outcomes.stream().filter(Outcome::exact).count();
        List<Outcome> emergencies = outcomes.stream().filter(o -> o.gold().expected() == Priority.EMERGENCIA).toList();
        long emergencyHits = emergencies.stream().filter(o -> o.predicted() == Priority.EMERGENCIA).count();
        long under = outcomes.stream().filter(Outcome::underTriaged).count();
        long over = outcomes.stream().filter(Outcome::overTriaged).count();

        int tp = 0;
        int fp = 0;
        int fn = 0;
        for (Outcome o : outcomes) {
            for (String s : o.predictedSymptoms()) {
                if (o.gold().expectedSymptoms().contains(s)) {
                    tp++;
                } else {
                    fp++;
                }
            }
            for (String s : o.gold().expectedSymptoms()) {
                if (!o.predictedSymptoms().contains(s)) {
                    fn++;
                }
            }
        }
        double precision = tp + fp == 0 ? 1 : (double) tp / (tp + fp);
        double recall = tp + fn == 0 ? 1 : (double) tp / (tp + fn);
        double f1 = precision + recall == 0 ? 0 : 2 * precision * recall / (precision + recall);
        int n = outcomes.size();
        return new Metrics(n, (double) exact / n, emergencies.isEmpty() ? 1 : (double) emergencyHits / emergencies.size(),
                emergencies.size(), (double) under / n, (double) over / n, precision, recall, f1, outcomes);
    }

    static String markdown(Metrics m) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Validação do motor de triagem — conjunto-ouro\n\n");
        sb.append("| Métrica | Valor |\n|---|---|\n");
        sb.append(String.format("| Casos | %d |%n", m.total()));
        sb.append(String.format("| Acurácia de prioridade (exata) | %.1f%% |%n", 100 * m.accuracy()));
        sb.append(String.format("| **Recall de emergência** (%d casos) | **%.1f%%** |%n", m.emergencyTotal(), 100 * m.emergencyRecall()));
        sb.append(String.format("| Sub-triagem (previu abaixo do esperado) | %.1f%% |%n", 100 * m.underTriageRate()));
        sb.append(String.format("| Sobre-triagem (previu acima do esperado) | %.1f%% |%n", 100 * m.overTriageRate()));
        sb.append(String.format("| Sintomas — precisão / recall / F1 | %.2f / %.2f / %.2f |%n%n", m.symptomPrecision(), m.symptomRecall(), m.symptomF1()));
        sb.append("## Divergências\n\n| Caso | Texto | Esperado | Previsto | Sintomas esperados | Sintomas previstos |\n|---|---|---|---|---|---|\n");
        m.outcomes().stream().filter(o -> !o.exact() || !o.predictedSymptoms().equals(o.gold().expectedSymptoms()))
                .forEach(o -> sb.append(String.format("| %s | %s | %s | %s | %s | %s |%n", o.gold().id(),
                        o.gold().text().replace("|", "/"), o.gold().expected().wire(), o.predicted().wire(),
                        new java.util.TreeSet<>(o.gold().expectedSymptoms()), new java.util.TreeSet<>(o.predictedSymptoms()))));
        return sb.toString();
    }
}
