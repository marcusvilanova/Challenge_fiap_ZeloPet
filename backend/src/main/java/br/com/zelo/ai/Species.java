package br.com.zelo.ai;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.text.Normalizer;
import java.util.Locale;

public enum Species {
    DOG("dog", "cachorro"),
    CAT("cat", "gato");

    private final String wire;
    private final String commonName;

    Species(String wire, String commonName) {
        this.wire = wire;
        this.commonName = commonName;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    public String commonName() {
        return commonName;
    }

    @JsonCreator
    public static Species fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Espécie é obrigatória");
        }
        String key = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
        return switch (key) {
            case "dog", "cao", "cachorro", "canino", "cadela" -> DOG;
            case "cat", "gato", "gata", "felino" -> CAT;
            default -> throw new IllegalArgumentException("Espécie não suportada: " + value);
        };
    }
}
