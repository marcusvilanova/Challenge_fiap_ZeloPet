package br.com.zelo.ai;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/** Próxima ação recomendada. Nunca inclui tratamento ou medicação. */
public enum NextAction {
    REGISTRAR_E_OBSERVAR("registrar_e_observar", "Registrar e observar por 24–48 horas"),
    AGENDAR_CONSULTA("agendar_consulta", "Agendar consulta nas próximas 24–72 horas"),
    CONTATAR_CLINICA("contatar_clinica_veterinaria", "Contatar a clínica veterinária hoje"),
    ATENDIMENTO_URGENTE("buscar_atendimento_urgente", "Buscar atendimento veterinário de urgência agora");

    private final String wire;
    private final String label;

    NextAction(String wire, String label) {
        this.wire = wire;
        this.label = label;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    public String label() {
        return label;
    }

    public static NextAction forPriority(Priority priority) {
        return switch (priority) {
            case BAIXA -> REGISTRAR_E_OBSERVAR;
            case MEDIA -> AGENDAR_CONSULTA;
            case ALTA -> CONTATAR_CLINICA;
            case EMERGENCIA -> ATENDIMENTO_URGENTE;
        };
    }

    @JsonCreator
    public static NextAction fromWire(String value) {
        return Arrays.stream(values())
                .filter(a -> a.wire.equalsIgnoreCase(value) || a.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Ação desconhecida: " + value));
    }
}
