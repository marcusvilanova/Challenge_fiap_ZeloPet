package br.com.zelo.domain;

import br.com.zelo.ai.NextAction;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class NextActionConverter implements AttributeConverter<NextAction, String> {

    @Override
    public String convertToDatabaseColumn(NextAction attribute) {
        return attribute == null ? null : attribute.wire();
    }

    @Override
    public NextAction convertToEntityAttribute(String dbData) {
        return dbData == null ? null : NextAction.fromWire(dbData);
    }
}
