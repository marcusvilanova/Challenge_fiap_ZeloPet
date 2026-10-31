package br.com.zelo.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.OptionalInt;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TextProcessingTest {

    @ParameterizedTest(name = "[{index}] \"{0}\" → \"{1}\"")
    @CsvSource(delimiter = ';', value = {
            "Vômito!;vomito |",
            "Não está comendo, nem bebendo água.;nao esta comendo | nem bebendo agua |",
            "CONVULSÃOOO!!!;convulsao |",
            "  CÃO   abatido  ;cao abatido",
            "tem 1000 gramas;tem 1000 gramas"})
    @DisplayName("normalize: minúsculas, sem acentos, pontuação vira limite de oração e números são preservados")
    void normalize_variousInputs(String raw, String expected) {
        assertThat(TextNormalizer.normalize(raw)).isEqualTo(expected.trim());
    }

    @Test
    @DisplayName("normalize: texto nulo vira string vazia")
    void normalize_null_returnsEmpty() {
        assertThat(TextNormalizer.normalize(null)).isEmpty();
    }

    @Test
    @DisplayName("typo: corrige uma letra faltando em palavra clínica")
    void typoCorrector_missingLetter_isCorrected() {
        TypoCorrector corrector = new TypoCorrector(Set.of("vomitou", "diarreia"));

        TypoCorrector.Result result = corrector.correct("ele vomtou ontem");

        assertThat(result.text()).isEqualTo("ele vomitou ontem");
        assertThat(result.corrections()).containsExactly("vomtou→vomitou");
    }

    @Test
    @DisplayName("typo: não altera palavras curtas nem palavras já conhecidas")
    void typoCorrector_shortOrKnownWords_areUntouched() {
        TypoCorrector corrector = new TypoCorrector(Set.of("vomitou"));

        assertThat(corrector.correct("ele come bem").text()).isEqualTo("ele come bem");
        assertThat(corrector.correct("vomitou").corrections()).isEmpty();
    }

    @Test
    @DisplayName("typo: transposição de letras conta como uma edição")
    void typoCorrector_transposition_isOneEdit() {
        assertThat(TypoCorrector.distance("convulsao", "covnulsao")).isEqualTo(1);
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" → {1} h")
    @CsvSource(delimiter = '|', value = {
            "esta assim ha 2 dias|48",
            "desde ontem|24",
            "mais de 3 dias|73",
            "ha 1 ou 2 dias|48",
            "faz uma semana|168",
            "ha duas semanas|336",
            "ha 5 horas|5",
            "so hoje de manha|8",
            "ha varios dias|72",
            "tem 3 dias que ele vomita|72",
            "com 2 dias de diarreia|48",
            "diarreia de 3 dias|72",
            "ha 3 meses|2160",
            "perdeu peso nos ultimos meses|720"})
    @DisplayName("duração: converte expressões do relato em horas")
    void durationParser_expressions(String text, int expectedHours) {
        assertThat(DurationParser.parseHours(text)).isEqualTo(OptionalInt.of(expectedHours));
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" é idade, não duração")
    @CsvSource(delimiter = '|', value = {
            "filhote de 3 meses vomitando",
            "ela tem 8 semanas",
            "cachorro com 2 meses de idade",
            "filhote com 45 dias"})
    @DisplayName("duração: idade do pet não é confundida com duração dos sintomas")
    void durationParser_ageIsNotDuration(String text) {
        assertThat(DurationParser.parseHours(text)).isEmpty();
    }
}
