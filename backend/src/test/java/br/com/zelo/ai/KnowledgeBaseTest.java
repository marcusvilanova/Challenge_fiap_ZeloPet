package br.com.zelo.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeBaseTest {

    private final KnowledgeBase knowledge = TestEngines.KNOWLEDGE;

    @Test
    @DisplayName("carrega o catálogo gerado a partir do D2")
    void loadDefault_hasCatalogAndD2Metadata() {
        assertThat(knowledge.symptoms()).hasSizeGreaterThan(60);
        assertThat(knowledge.d2().rowsRaw()).isEqualTo(10_000);
        assertThat(knowledge.d2().duplicateRowsRemoved()).isEqualTo(20);
    }

    @Test
    @DisplayName("todo sintoma tem severidade 1–4 e padrões compilados")
    void everySymptom_isWellFormed() {
        assertThat(knowledge.symptoms()).allSatisfy(s -> {
            assertThat(s.baseSeverity()).isBetween(1, 4);
            assertThat(s.patterns()).isNotEmpty();
        });
    }

    @Test
    @DisplayName("os sinais de alerta exigidos pela Sprint 3 existem e são severidade 4")
    void redFlags_exist() {
        List<String> required = List.of("convulsao", "dificuldade_respirar", "colapso", "ingestao_toxica",
                "nao_urina", "vomito_sangue", "trauma", "paralisia", "ansia_sem_vomitar", "sangramento_intenso");

        assertThat(required).allSatisfy(id -> assertThat(knowledge.symptom(id).baseSeverity()).isEqualTo(4));
    }

    @Test
    @DisplayName("ids de sintomas usados nas regras de prioridade existem no catálogo")
    void rulesReferenceExistingSymptoms() {
        List<String> referenced = List.of("apatia", "vomito", "vomito_repetido", "vomito_sangue", "diarreia",
                "diarreia_sangue", "fezes_escuras", "febre", "perda_apetite", "dor", "dor_abdominal", "tosse",
                "respiracao_ofegante", "recusa_agua", "desidratacao", "fraqueza", "intolerancia_exercicio",
                "sibilo", "sede_urina_excessiva", "xixi_fora_lugar", "salivacao_excessiva", "abdomen_distendido",
                "mal_estar_inespecifico");

        assertThat(referenced).allSatisfy(id -> assertThat(knowledge.knows(id)).as(id).isTrue());
    }

    @Test
    @DisplayName("sintoma desconhecido lança exceção com o id")
    void symptom_unknown_throws() {
        assertThatThrownBy(() -> knowledge.symptom("nao_existe")).hasMessageContaining("nao_existe");
    }

    @Test
    @DisplayName("raças vêm do D2 e braquicefálicas são marcadas")
    void breeds_comeFromD2() {
        assertThat(knowledge.breeds(Species.DOG)).isNotEmpty();
        assertThat(knowledge.isBrachycephalic(Species.DOG, "Bulldog")).isTrue();
        assertThat(knowledge.isBrachycephalic(Species.DOG, "Beagle")).isFalse();
        assertThat(knowledge.isBrachycephalic(Species.DOG, null)).isFalse();
    }

    @Test
    @DisplayName("frequência de referência do D2 é uma fração entre 0 e 1")
    void referenceFrequency_isFraction() {
        assertThat(knowledge.symptom("febre").referenceFrequency(Species.DOG)).isBetween(0.1, 0.6);
    }
}
