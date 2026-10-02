package com.vtesdecks.jpa.entity.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;
import com.vtesdecks.model.ArchetypeCardRequirement;

@Converter
public class ArchetypeCardRequirementsConverter implements AttributeConverter<List<ArchetypeCardRequirement>, String> {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<ArchetypeCardRequirement> values) {
        try {
            return JSON.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid archetype card requirements", e);
        }
    }

    @Override
    public List<ArchetypeCardRequirement> convertToEntityAttribute(String value) {
        if (value == null || value.isBlank() || "null".equals(value)) {
            return List.of();
        }
        try {
            return JSON.readValue(value, new TypeReference<List<ArchetypeCardRequirement>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid stored archetype card requirements", e);
        }
    }
}
