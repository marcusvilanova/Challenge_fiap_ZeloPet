package br.com.zelo.domain;

import br.com.zelo.ai.Priority;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persiste a prioridade pelo valor do contrato ("baixa", "media", "alta", "emergencia"). */
@Converter
public class PriorityConverter implements AttributeConverter<Priority, String> {

    @Override
    public String convertToDatabaseColumn(Priority attribute) {
        return attribute == null ? null : attribute.wire();
    }

    @Override
    public Priority convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Priority.fromWire(dbData);
    }
}
