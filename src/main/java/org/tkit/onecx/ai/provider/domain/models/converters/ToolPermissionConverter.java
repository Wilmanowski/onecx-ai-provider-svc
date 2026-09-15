package org.tkit.onecx.ai.provider.domain.models.converters;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import org.tkit.onecx.ai.provider.domain.models.enums.ToolPermission;

@Converter
public class ToolPermissionConverter implements AttributeConverter<ToolPermission, String> {

    @Override
    public String convertToDatabaseColumn(ToolPermission attribute) {
        return (attribute == null ? ToolPermission.DEFAULT : attribute).toCanonical().name();
    }

    @Override
    public ToolPermission convertToEntityAttribute(String dbData) {
        return ToolPermission.fromValueOrDefault(dbData).toCanonical();
    }
}
