package br.com.zelo.ai;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/** Prioridade preliminar da triagem. O valor serializado segue o contrato da Sprint 3. */
public enum Priority {
    BAIXA("baixa", 1),
    MEDIA("media", 2),
    ALTA("alta", 3),
    EMERGENCIA("emergencia", 4);

    private final String wire;
    private final int level;

    Priority(String wire, int level) {
        this.wire = wire;
        this.level = level;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    public int level() {
        return level;
    }

    public static Priority fromLevel(int level) {
        int clamped = Math.max(1, Math.min(4, level));
        return Arrays.stream(values()).filter(p -> p.level == clamped).findFirst().orElseThrow();
    }

    @JsonCreator
    public static Priority fromWire(String value) {
        return Arrays.stream(values())
                .filter(p -> p.wire.equalsIgnoreCase(value) || p.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Prioridade desconhecida: " + value));
    }

    public static Priority max(Priority a, Priority b) {
        return a.level >= b.level ? a : b;
    }

    public Priority raisedBy(int steps, Priority cap) {
        return fromLevel(Math.min(level + steps, cap.level));
    }
}
