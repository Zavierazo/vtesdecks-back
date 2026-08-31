package com.vtesdecks.api.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vtesdecks.jpa.entity.UserSearchPresetEntity;
import com.vtesdecks.model.api.ApiSearchPreset;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public abstract class ApiSearchPresetMapper {
    @Autowired
    private ObjectMapper objectMapper;

    @Mapping(target = "params", source = "params", qualifiedByName = "jsonToMap")
    public abstract ApiSearchPreset mapToApi(UserSearchPresetEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "creationDate", ignore = true)
    @Mapping(target = "modificationDate", ignore = true)
    @Mapping(target = "params", source = "params", qualifiedByName = "mapToJson")
    public abstract UserSearchPresetEntity mapToEntity(ApiSearchPreset api);

    @Named("jsonToMap")
    protected Map<String, String> jsonToMap(JsonNode value) {
        return objectMapper.convertValue(value, new TypeReference<>() {});
    }

    @Named("mapToJson")
    protected JsonNode mapToJson(Map<String, String> value) {
        return objectMapper.valueToTree(value);
    }
}
