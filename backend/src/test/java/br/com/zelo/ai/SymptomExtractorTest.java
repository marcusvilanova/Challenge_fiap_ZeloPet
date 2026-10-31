package br.com.zelo.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static br.com.zelo.ai.TestEngines.EXTRACTOR;
import static org.assertj.core.api.Assertions.assertThat;

class SymptomExtractorTest {

    private static SymptomExtractor.Extraction extract(String text) {
        return EXTRACTOR.extract(text, Species.DOG);
    }

    @Test
    @DisplayName("extrai vários sintomas de um relato coloquial")
    void extract_colloquialReport_findsAllSymptoms() {
        var result = extract("Meu cão está muito abatido, sem apetite e vomitou três vezes");

        assertThat(result.active()).extracting(SymptomMention::id)
                .containsExactlyInAnyOrder("apatia", "perda_apetite", "vomito_repetido");
    }

    @Test
    @DisplayName("negação direta: 'não vomitou' não conta como sintoma ativo")
    void extract_directNegation_isNegated() {
        var result = extract("Ele não vomitou, só está com tosse");

        assertThat(result.active()).extracting(SymptomMention::id).containsExactly("tosse");
        assertThat(result.negated()).extracting(SymptomMention::id).containsExactly("vomito");
    }

    @Test
    @DisplayName("negação com 'nem' alcança o segundo sintoma da lista")
    void extract_nemNegatesSecondItem() {
        var result = extract("não tem febre nem tosse");

        assertThat(result.active()).isEmpty();
        assertThat(result.negated()).extracting(SymptomMention::id).containsExactlyInAnyOrder("febre", "tosse");
    }

    @Test
    @DisplayName("sintoma resolvido: 'a diarreia já passou' e 'parou de vomitar'")
    void extract_resolvedSymptoms_areNegated() {
        assertThat(extract("a diarreia já passou").negated()).extracting(SymptomMention::id).containsExactly("diarreia");
        assertThat(extract("parou de vomitar").negated()).extracting(SymptomMention::id).containsExactly("vomito");
    }

    @Test
    @DisplayName("dupla negação: 'não parou de espirrar' = continua espirrando")
    void extract_doubleNegation_keepsSymptomActive() {
        var result = extract("ele não parou de espirrar");

        assertThat(result.active()).extracting(SymptomMention::id).containsExactly("espirros");
    }

    @Test
    @DisplayName("'passou mal' significa que piorou: não é sintoma resolvido")
    void extract_passouMal_isNotResolution() {
        assertThat(extract("ontem ele passou mal").negated()).isEmpty();
    }

    @Test
    @DisplayName("negação não atravessa 'mas' nem o limite da oração")
    void extract_negationStopsAtBoundaries() {
        var result = extract("não come mas vomita muito. Sem febre");

        assertThat(result.active()).extracting(SymptomMention::id).contains("perda_apetite", "vomito_repetido");
        assertThat(result.negated()).extracting(SymptomMention::id).containsExactly("febre");
    }

    @Test
    @DisplayName("'não sei se foi convulsão' é incerteza: o sinal de alerta continua ativo")
    void extract_uncertainRedFlag_staysActive() {
        var result = extract("não sei se foi convulsão ou susto");

        assertThat(result.active()).extracting(SymptomMention::id).contains("convulsao");
    }

    @Test
    @DisplayName("na mesma família vence o sintoma mais grave (vômito com sangue engole vômito)")
    void extract_family_keepsMostSevere() {
        var result = extract("vomitou sangue");

        assertThat(result.active()).extracting(SymptomMention::id).containsExactly("vomito_sangue");
    }

    @Test
    @DisplayName("severidade depende da espécie: dificuldade para urinar é emergência em gatos")
    void extract_speciesSpecificSeverity() {
        String text = "faz força para fazer xixi";

        assertThat(EXTRACTOR.extract(text, Species.DOG).active().get(0).severity()).isEqualTo(3);
        assertThat(EXTRACTOR.extract(text, Species.CAT).active().get(0).severity()).isEqualTo(4);
    }

    @Test
    @DisplayName("erros de digitação são corrigidos e registrados")
    void extract_typos_areCorrected() {
        var result = extract("meu cachoro vomtou 4 vezes");

        assertThat(result.typoCorrections()).isNotEmpty();
        assertThat(result.active()).extracting(SymptomMention::id).containsExactly("vomito_repetido");
    }

    @Test
    @DisplayName("texto sem sintomas devolve lista vazia")
    void extract_noSymptoms_isEmpty() {
        assertThat(extract("oi, tudo bem? gostaria de saber o horário").mentions()).isEmpty();
    }
}
