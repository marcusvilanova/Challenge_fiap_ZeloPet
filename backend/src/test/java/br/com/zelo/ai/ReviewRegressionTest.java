package br.com.zelo.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static br.com.zelo.ai.TestEngines.EXTRACTOR;
import static br.com.zelo.ai.TestEngines.analyze;
import static br.com.zelo.ai.TestEngines.cat;
import static br.com.zelo.ai.TestEngines.dog;
import static org.assertj.core.api.Assertions.assertThat;

/** Regressões dos achados da revisão de código: nenhum deles pode voltar a rebaixar uma triagem. */
class ReviewRegressionTest {

    private static SymptomExtractor.Extraction extract(String text) {
        return EXTRACTOR.extract(text, Species.DOG);
    }

    // ------------------------------------------------------------ C1: sintoma que continua não é "resolvido"
    @ParameterizedTest(name = "[{index}] \"{0}\" mantém {1} como presente")
    @CsvSource(delimiter = '|', value = {
            "a convulsão não parou|convulsao",
            "o vômito não parou|vomito",
            "a tosse não passou|tosse",
            "ele melhorou mas a tosse não passou|tosse",
            "ele não para de ofegar|respiracao_ofegante",
            "não para de tremer|tremores",
            "o vômito parou ontem mas hoje voltou|vomito",
            "ele vomitou e parou de comer|vomito"})
    @DisplayName("C1: 'não parou/não passou/não para de' mantém o sintoma ativo")
    void continuingSymptom_staysActive(String text, String symptomId) {
        assertThat(extract(text).active()).extracting(SymptomMention::id).contains(symptomId);
    }

    @Test
    @DisplayName("C1: a convulsão que não parou é emergência, não 'baixa prioridade'")
    void convulsionThatDidNotStop_isEmergency() {
        assertThat(analyze("a convulsão não parou", dog()).priority()).isEqualTo(Priority.EMERGENCIA);
    }

    @Test
    @DisplayName("C1: resolução verdadeira continua funcionando ('a tosse passou', 'o vômito parou')")
    void realResolution_stillNegates() {
        assertThat(extract("a tosse passou").negated()).extracting(SymptomMention::id).containsExactly("tosse");
        assertThat(extract("o vômito parou").negated()).extracting(SymptomMention::id).containsExactly("vomito");
    }

    @Test
    @DisplayName("C1: 'a diarreia já passou mas ainda está sem apetite' resolve a diarreia e mantém a falta de apetite")
    void resolvedSymptomFollowedByAnotherComplaint_isStillResolved() {
        var result = extract("a diarreia já passou mas ainda está sem apetite");

        assertThat(result.negated()).extracting(SymptomMention::id).containsExactly("diarreia");
        assertThat(result.active()).extracting(SymptomMention::id).contains("perda_apetite");
    }

    // --------------------------------------- H1: a negação vem antes da escolha do mais grave da família
    @Test
    @DisplayName("H1: 'vomitou, não tem sangue no vômito' mantém o vômito presente")
    void negatedSevereMember_doesNotEraseMildMember() {
        var result = extract("ele vomitou, não tem sangue no vômito");

        assertThat(result.active()).extracting(SymptomMention::id).contains("vomito");
        assertThat(result.active()).extracting(SymptomMention::id).doesNotContain("vomito_sangue");
    }

    @Test
    @DisplayName("H1: 'ofegante mas sem dificuldade para respirar' mantém o ofegar presente")
    void panting_notErasedByNegatedBreathingDifficulty() {
        var result = extract("está ofegante mas sem dificuldade para respirar");

        assertThat(result.active()).extracting(SymptomMention::id).contains("respiracao_ofegante");
    }

    // ----------------------------------------------- H2: "não está animado" não é frase tranquilizadora
    @ParameterizedTest(name = "[{index}] \"{0}\" não é tranquilizador")
    @ValueSource(strings = {"ele não está animado", "não está brincando", "ele não está feliz", "não está tranquilo"})
    @DisplayName("H2: queixa negada vira 'não reconhecido' (média + revisão humana), nunca baixa")
    void negatedReassurance_isNotLowPriority(String text) {
        var analysis = analyze(text, dog());

        assertThat(analysis.priority()).isNotEqualTo(Priority.BAIXA);
        assertThat(analysis.unrecognized()).isTrue();
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" continua tranquilizador")
    @ValueSource(strings = {"ele está bem, brincando e comendo normal", "tudo bem com ele, só queria saber das vacinas"})
    @DisplayName("H2: frase realmente tranquilizadora segue sem alarme")
    void genuineReassurance_isNotUnrecognized(String text) {
        assertThat(analyze(text, dog()).unrecognized()).isFalse();
    }

    // ------------------------------------------------ H3: "está com 3 dias sem comer" é duração, não idade
    @Test
    @DisplayName("H3: 'está com 3 dias sem comer' conta como 72 h")
    void withNDays_isDuration() {
        assertThat(DurationParser.parseHours("ele esta com 3 dias sem comer")).hasValue(72);
    }

    @Test
    @DisplayName("H3: gato 'com 2 dias sem comer' é ALTA (regra do gato sem comer por 48 h)")
    void catWithTwoDaysNotEating_isHigh() {
        assertThat(analyze("ele está com 2 dias sem comer", cat()).priority().level()).isGreaterThanOrEqualTo(Priority.ALTA.level());
    }

    @Test
    @DisplayName("H3: idade continua sendo idade ('filhote com 3 meses', 'aos 2 meses', '4 semanas de vida')")
    void ageIsStillAge() {
        assertThat(DurationParser.parseHours("filhote com 3 meses")).isEmpty();
        assertThat(DurationParser.parseHours("adotei aos 2 meses")).isEmpty();
        assertThat(DurationParser.parseHours("ele tem 4 semanas de vida")).isEmpty();
    }

    // ------------------------------------------- M1/M2: negação dentro do trecho casado não gera alarme
    @ParameterizedTest(name = "[{index}] \"{0}\" não é sangue")
    @ValueSource(strings = {"vomitou sem sangue", "vomitou mas não tem sangue no vômito", "diarreia sem sangue"})
    @DisplayName("M1: 'sem sangue' não aciona vômito/diarreia com sangue")
    void withoutBlood_isNotBloodSymptom(String text) {
        assertThat(extract(text).active()).extracting(SymptomMention::id)
                .doesNotContain("vomito_sangue", "diarreia_sangue");
    }

    @Test
    @DisplayName("M1: 'vomitou sangue' continua sendo emergência")
    void vomitingBlood_isStillEmergency() {
        assertThat(analyze("ele vomitou sangue", dog()).priority()).isEqualTo(Priority.EMERGENCIA);
    }

    @Test
    @DisplayName("M2: 'não anda normal' não é paralisia; 'não anda' continua sendo")
    void doesNotWalkNormally_isNotParalysis() {
        assertThat(extract("ele não anda normal").active()).extracting(SymptomMention::id).doesNotContain("paralisia");
        assertThat(extract("ele não anda").active()).extracting(SymptomMention::id).contains("paralisia");
    }
}
