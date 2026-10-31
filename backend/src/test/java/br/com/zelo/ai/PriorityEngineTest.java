package br.com.zelo.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static br.com.zelo.ai.TestEngines.analyze;
import static br.com.zelo.ai.TestEngines.cat;
import static br.com.zelo.ai.TestEngines.dog;
import static br.com.zelo.ai.TestEngines.pet;
import static org.assertj.core.api.Assertions.assertThat;

class PriorityEngineTest {

    @Test
    @DisplayName("sinal de alerta leva a emergência e tem confiança mínima de 0,90")
    void redFlag_isEmergencyWithHighConfidence() {
        TriageAnalysis a = analyze("teve uma convulsão", dog());

        assertThat(a.priority()).isEqualTo(Priority.EMERGENCIA);
        assertThat(a.redFlagIds()).containsExactly("convulsao");
        assertThat(a.confidence()).isGreaterThanOrEqualTo(0.90);
        assertThat(a.humanReviewRequired()).isTrue();
    }

    @Test
    @DisplayName("regra Sprint 3: abatimento + vômito = alta, com revisão humana")
    void lethargyWithVomiting_isHigh() {
        TriageAnalysis a = analyze("meu cão está abatido e vomitou duas vezes", dog());

        assertThat(a.priority()).isEqualTo(Priority.ALTA);
        assertThat(a.rules()).contains("R2_ABATIMENTO_COMBINADO");
        assertThat(a.humanReviewRequired()).isTrue();
    }

    @Test
    @DisplayName("filhote sobe um nível e o fator aparece nos fatores contribuintes")
    void puppy_bumpsPriorityAndIsListedAsFactor() {
        TriageAnalysis adult = analyze("ele vomitou", dog());
        TriageAnalysis puppy = analyze("ele vomitou", pet(Species.DOG, 4, 3.0, false, Set.of()));

        assertThat(adult.priority()).isEqualTo(Priority.MEDIA);
        assertThat(puppy.priority()).isEqualTo(Priority.ALTA);
        assertThat(puppy.contextFactors()).contains("filhote");
        assertThat(adult.contextFactors()).isEmpty();
    }

    @Test
    @DisplayName("modificadores de contexto nunca levam a emergência por si só")
    void contextModifiers_capAtHigh() {
        TriageAnalysis a = analyze("ele vomitou", pet(Species.DOG, 3, 2.0, false, Set.of("doenca_renal", "nao_vacinado")));

        assertThat(a.priority()).isEqualTo(Priority.ALTA);
    }

    @Test
    @DisplayName("persistência: diarreia há mais de 48 h sobe de média para alta")
    void persistence_raisesGastrointestinalSigns() {
        assertThat(analyze("diarreia", dog()).priority()).isEqualTo(Priority.MEDIA);
        assertThat(analyze("diarreia há 3 dias", dog()).priority()).isEqualTo(Priority.ALTA);
    }

    @Test
    @DisplayName("gato sem comer há 24 h ou mais é alta")
    void catNotEating_isHigh() {
        assertThat(analyze("não come há 1 dia", cat()).priority()).isEqualTo(Priority.ALTA);
        assertThat(analyze("não come há 1 dia", dog()).priority()).isEqualTo(Priority.MEDIA);
    }

    @Test
    @DisplayName("cão com barriga inchada e salivação: suspeita de torção = emergência")
    void bloatCompanions_areEmergencyOnlyForDogs() {
        assertThat(analyze("barriga inchada e babando", dog()).priority()).isEqualTo(Priority.EMERGENCIA);
        assertThat(analyze("barriga inchada e babando", cat()).priority()).isEqualTo(Priority.ALTA);
    }

    @Test
    @DisplayName("sem sintomas: prioridade baixa, confiança mínima e sem revisão por prioridade")
    void noSymptoms_lowPriorityLowConfidence() {
        TriageAnalysis a = analyze("ele está comendo bem e brincando", dog());

        assertThat(a.hasSymptoms()).isFalse();
        assertThat(a.priority()).isEqualTo(Priority.BAIXA);
        assertThat(a.confidence()).isEqualTo(0.20);
        assertThat(a.humanReviewRequired()).isTrue();
    }

    @Test
    @DisplayName("relato sem nenhum sinal reconhecido NÃO vira 'baixa': média + revisão humana")
    void unrecognizedComplaint_isMediumNotLow() {
        TriageAnalysis a = analyze("meu cachorro tem um negócio no zóio", dog());

        assertThat(a.unrecognized()).isTrue();
        assertThat(a.priority()).isEqualTo(Priority.MEDIA);
        assertThat(a.rules()).containsExactly("R13_RELATO_NAO_RECONHECIDO");
        assertThat(a.humanReviewRequired()).isTrue();
    }

    @Test
    @DisplayName("negar todos os sintomas é tranquilizador: prioridade baixa")
    void explicitlyNegatedSymptoms_areLowPriority() {
        TriageAnalysis a = analyze("não vomitou e não tem diarreia", dog());

        assertThat(a.unrecognized()).isFalse();
        assertThat(a.priority()).isEqualTo(Priority.BAIXA);
        assertThat(a.negated()).hasSize(2);
    }

    @Test
    @DisplayName("queixa vaga fica com confiança baixa e exige revisão humana")
    void vagueComplaint_hasLowConfidence() {
        TriageAnalysis a = analyze("meu cachorro está estranho", dog());

        assertThat(a.vague()).isTrue();
        assertThat(a.confidence()).isLessThanOrEqualTo(0.45);
        assertThat(a.humanReviewRequired()).isTrue();
    }

    @Test
    @DisplayName("conversa em vários turnos: sintomas anteriores são mantidos e a duração é herdada")
    void multiTurn_mergesPriorSymptomsAndDuration() {
        TriageAnalysis first = analyze("ele está vomitando", dog());
        TriageAnalysis second = TestEngines.ENGINE.analyze("tem 3 dias, e está bem abatido", dog(),
                first.active(), first.durationHours());

        assertThat(second.active()).extracting(SymptomMention::id).containsExactlyInAnyOrder("vomito", "apatia");
        assertThat(second.durationHours()).isEqualTo(72);
        assertThat(second.priority()).isEqualTo(Priority.ALTA);
    }

    @Test
    @DisplayName("negação em turno posterior remove o sintoma coletado antes")
    void multiTurn_laterNegationRemovesSymptom() {
        TriageAnalysis first = analyze("ele está vomitando e com diarreia", dog());
        TriageAnalysis second = TestEngines.ENGINE.analyze("na verdade não está vomitando", dog(),
                first.active(), null);

        assertThat(second.active()).extracting(SymptomMention::id).containsExactly("diarreia");
    }
}
