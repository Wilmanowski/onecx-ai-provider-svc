package org.tkit.onecx.ai.provider.domain.models.converters;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import org.tkit.onecx.ai.provider.domain.models.enums.ExecutionPolicy;

@Converter
public class ExecutionPolicyConverter implements AttributeConverter<ExecutionPolicy, String> {

    @Override
    public String convertToDatabaseColumn(ExecutionPolicy attribute) {
        return (attribute == null ? ExecutionPolicy.DEFAULT : attribute).toCanonical().name();
    }

    @Override
    public ExecutionPolicy convertToEntityAttribute(String dbData) {
        return ExecutionPolicy.fromValueOrDefault(dbData).toCanonical();
    }
}
