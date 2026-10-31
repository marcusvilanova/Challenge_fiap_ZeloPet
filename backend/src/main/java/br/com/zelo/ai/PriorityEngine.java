package br.com.zelo.ai;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Camada determinística de segurança clínica. A prioridade final é sempre o MÁXIMO entre a
 * severidade dos sintomas e as regras de combinação/contexto; nenhuma camada posterior
 * (inclusive o LLM) pode reduzi-la.
 *
 * <p>As regras abaixo são da equipe e precisam de revisão por médico-veterinário antes de uso real.
 */
public final class PriorityEngine {

    private static final Set<String> VOMIT = Set.of("vomito", "vomito_repetido", "vomito_sangue");
    private static final Set<String> DIARRHEA = Set.of("diarreia", "diarreia_sangue", "fezes_escuras");
    private static final Set<String> COMBINES_WITH_LETHARGY = Set.of(
            "vomito", "vomito_repetido", "diarreia", "diarreia_sangue", "febre", "perda_apetite", "dor",
            "dor_abdominal", "tosse", "respiracao_ofegante", "recusa_agua", "desidratacao", "tremores");
    private static final Set<String> PERSISTENCE_CANDIDATES = Set.of(
            "vomito", "vomito_repetido", "diarreia", "perda_apetite", "apatia", "febre", "fraqueza");
    private static final Set<String> BLOAT_COMPANIONS = Set.of(
            "salivacao_excessiva", "dor", "dor_abdominal", "apatia", "respiracao_ofegante", "vomito", "fraqueza",
            "mudanca_comportamento");
    private static final Set<String> CARDIAC_SIGNS = Set.of(
            "tosse", "intolerancia_exercicio", "respiracao_ofegante", "apatia", "sibilo");
    private static final Set<String> RENAL_SIGNS = Set.of(
            "sede_urina_excessiva", "vomito", "vomito_repetido", "perda_apetite", "apatia", "xixi_fora_lugar");
    private static final Set<String> BRACHY_SIGNS = Set.of("respiracao_ofegante", "sibilo", "tosse");

    private static final int PERSISTENCE_HOURS = 48;
    private static final int ONE_WEEK_HOURS = 168;
    private static final int CAT_ANOREXIA_HOURS = 24;
    private static final int MULTI_SYMPTOM_COUNT = 3;

    public record Decision(Priority priority, List<String> reasons, List<String> redFlagIds,
                           List<String> contextFactors, List<String> rules) {
    }

    public Decision decide(List<SymptomMention> active, PetContext pet, Integer durationHours) {
        Run run = new Run(active, pet, durationHours);
        run.applyBaseSeverity();
        run.applyCombinations();
        run.applyPersistence();
        run.applyPetContext();
        return run.result();
    }

    /** Estado mutável de uma única avaliação (nunca compartilhado entre chamadas). */
    private static final class Run {
        private final List<SymptomMention> active;
        private final Set<String> ids;
        private final PetContext pet;
        private final Integer hours;
        private final Set<String> reasons = new LinkedHashSet<>();
        private final Set<String> factors = new LinkedHashSet<>();
        private final Set<String> rules = new LinkedHashSet<>();
        private final List<String> redFlags = new ArrayList<>();
        private int level = 1;

        Run(List<SymptomMention> active, PetContext pet, Integer hours) {
            this.active = active;
            this.ids = new LinkedHashSet<>(active.stream().map(SymptomMention::id).toList());
            this.pet = pet;
            this.hours = hours;
        }

        void applyBaseSeverity() {
            for (SymptomMention m : active) {
                level = Math.max(level, m.severity());
                if (m.redFlag()) {
                    redFlags.add(m.id());
                    reasons.add("Sinal de alerta relatado: " + m.label().toLowerCase());
                    rules.add("R1_SINAL_DE_ALERTA");
                }
            }
            if (redFlags.isEmpty() && level >= Priority.ALTA.level()) {
                active.stream().filter(m -> m.severity() >= Priority.ALTA.level()).findFirst().ifPresent(m ->
                        reasons.add("Sinal que exige avaliação veterinária: " + m.label().toLowerCase()));
                rules.add("R0_SEVERIDADE_DO_SINTOMA");
            }
        }

        void applyCombinations() {
            if (has("apatia") && anyOf(COMBINES_WITH_LETHARGY)) {
                raiseTo(Priority.ALTA, "R2_ABATIMENTO_COMBINADO",
                        "Abatimento junto de outros sinais indica que o organismo está sendo afetado");
            }
            if (anyOf(VOMIT) && anyOf(DIARRHEA)) {
                boolean fragile = pet.isPuppy() || pet.isSenior() || longerThan(24) || has("vomito_repetido");
                raiseTo(fragile ? Priority.ALTA : Priority.MEDIA, "R3_VOMITO_E_DIARREIA",
                        "Vômito e diarreia juntos aumentam o risco de desidratação");
            }
            if (pet.species() == Species.DOG && has("abdomen_distendido") && anyOf(BLOAT_COMPANIONS)) {
                redFlags.add("abdomen_distendido");
                raiseTo(Priority.EMERGENCIA, "R12_SUSPEITA_DE_TORCAO",
                        "Barriga inchada junto de outros sinais pode indicar torção de estômago, que é emergência");
            }
            long significant = active.stream().filter(m -> m.severity() >= Priority.MEDIA.level()).count();
            if (significant >= MULTI_SYMPTOM_COUNT) {
                raiseTo(Priority.ALTA, "R4_VARIOS_SINAIS", "Vários sinais ao mesmo tempo merecem avaliação profissional");
            }
        }

        void applyPersistence() {
            if (longerThan(PERSISTENCE_HOURS) && anyOf(PERSISTENCE_CANDIDATES)) {
                bump("R5_PERSISTENCIA", "Sinais que duram mais de 48 horas não devem ser apenas observados",
                        "duração acima de 48 horas");
            }
            if (hours != null && hours >= ONE_WEEK_HOURS && !active.isEmpty()) {
                raiseTo(Priority.MEDIA, "R5_PERSISTENCIA_SEMANA", "Sinais há uma semana ou mais pedem consulta");
            }
            if (pet.species() == Species.CAT && has("perda_apetite") && hours != null && hours >= CAT_ANOREXIA_HOURS) {
                raiseTo(Priority.ALTA, "R6_GATO_SEM_COMER",
                        "Gatos que ficam sem comer por 24 horas ou mais precisam de avaliação");
            }
        }

        void applyPetContext() {
            boolean hasSignificant = active.stream().anyMatch(m -> m.severity() >= Priority.MEDIA.level());
            if (hasSignificant && pet.isPuppy()) {
                bump("R7_FILHOTE", pet.name() + " é filhote, e filhotes pioram e se desidratam mais rápido", "filhote");
            }
            if (hasSignificant && pet.isSenior()) {
                bump("R8_IDOSO", pet.name() + " já é idoso, o que pede mais cautela", "idoso");
            }
            if (pet.has("cardiopatia") && anyOf(CARDIAC_SIGNS)) {
                bump("R9_HISTORICO_CARDIACO", "O histórico cardíaco torna esses sinais mais relevantes",
                        "histórico cardíaco");
            }
            if ((pet.has("doenca_renal") || pet.has("doenca_cronica")) && anyOf(RENAL_SIGNS)) {
                bump("R9_HISTORICO_RENAL_CRONICO", "O histórico de saúde torna esses sinais mais relevantes",
                        pet.has("doenca_renal") ? "histórico renal" : "doença crônica");
            }
            if (pet.isPuppy() && pet.has("nao_vacinado") && (anyOf(VOMIT) || anyOf(DIARRHEA))
                    && raiseTo(Priority.ALTA, "R10_FILHOTE_SEM_VACINA",
                    "Filhote sem vacinas em dia com vômito ou diarreia precisa de avaliação rápida")) {
                factors.add("sem vacinação em dia");
            }
            if (pet.brachycephalic() && anyOf(BRACHY_SIGNS)) {
                bump("R11_BRAQUICEFALICO", "Raças de focinho curto têm mais risco respiratório",
                        "raça braquicefálica");
            }
        }

        Decision result() {
            Priority priority = Priority.fromLevel(active.isEmpty() ? 1 : level);
            return new Decision(priority, List.copyOf(reasons), List.copyOf(redFlags),
                    List.copyOf(factors), List.copyOf(rules));
        }

        private boolean has(String id) {
            return ids.contains(id);
        }

        private boolean anyOf(Set<String> candidates) {
            return ids.stream().anyMatch(candidates::contains);
        }

        private boolean longerThan(int limitHours) {
            return hours != null && hours > limitHours;
        }

        /** Garante ao menos o nível alvo; retorna se a regra mudou a prioridade. */
        private boolean raiseTo(Priority target, String rule, String reason) {
            boolean changed = target.level() > level;
            if (changed) {
                level = target.level();
            }
            if (target.level() >= Priority.MEDIA.level()) {
                reasons.add(reason);
                rules.add(rule);
            }
            return changed;
        }

        /** Sobe um nível, no máximo até ALTA: só sinais de alerta explícitos levam a emergência. */
        private void bump(String rule, String reason, String factor) {
            if (level < Priority.ALTA.level()) {
                level++;
                reasons.add(reason);
                rules.add(rule);
                factors.add(factor);
            }
        }
    }
}
