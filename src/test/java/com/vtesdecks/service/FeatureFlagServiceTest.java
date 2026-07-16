package com.vtesdecks.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vtesdecks.api.mapper.ApiFeatureFlagMapper;
import com.vtesdecks.enums.FeatureFlagType;
import com.vtesdecks.jpa.entity.FeatureFlagEntity;
import com.vtesdecks.jpa.repositories.FeatureFlagRepository;
import com.vtesdecks.model.api.ApiFeatureFlag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class FeatureFlagServiceTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private FeatureFlagRepository repository;
    @Mock
    private ApiFeatureFlagMapper mapper;
    @InjectMocks
    private FeatureFlagService service;

    @Test
    public void getAllReturnsMappedFlags() {
        FeatureFlagEntity entity = entity("sample_feature", FeatureFlagType.BOOLEAN, "false");
        ApiFeatureFlag api = ApiFeatureFlag.builder().key("sample_feature").build();
        when(repository.findAll()).thenReturn(List.of(entity));
        when(mapper.map(List.of(entity))).thenReturn(List.of(api));

        assertEquals(List.of(api), service.getAll());
    }

    @Test
    public void updateUnknownKeyReturnsEmpty() {
        when(repository.findByKey("missing")).thenReturn(Optional.empty());

        assertTrue(service.update("missing", json("true")).isEmpty());
        verify(repository, never()).save(any());
    }

    @ParameterizedTest
    @CsvSource({
            "BOOLEAN, true",
            "STRING, '\"hello\"'",
            "LIST, '[\"a\",\"b\"]'",
            "LIST, '[]'",
    })
    public void updateWithValidValueSaves(FeatureFlagType type, String value) {
        FeatureFlagEntity entity = entity("flag", type, "null");
        ApiFeatureFlag api = ApiFeatureFlag.builder().key("flag").build();
        when(repository.findByKey("flag")).thenReturn(Optional.of(entity));
        when(repository.save(entity)).thenReturn(entity);
        when(mapper.map(entity)).thenReturn(api);

        Optional<ApiFeatureFlag> result = service.update("flag", json(value));

        assertEquals(Optional.of(api), result);
        assertEquals(json(value), entity.getValue());
    }

    @ParameterizedTest
    @CsvSource({
            "BOOLEAN, '\"true\"'",
            "BOOLEAN, 1",
            "BOOLEAN, null",
            "STRING, true",
            "STRING, '[\"a\"]'",
            "STRING, null",
            "LIST, '\"a\"'",
            "LIST, '[1,2]'",
            "LIST, '[\"a\",1]'",
            "LIST, '[{\"a\":1}]'",
            "LIST, null",
    })
    public void updateWithInvalidValueThrows(FeatureFlagType type, String value) {
        FeatureFlagEntity entity = entity("flag", type, "null");
        when(repository.findByKey("flag")).thenReturn(Optional.of(entity));

        assertThrows(IllegalArgumentException.class, () -> service.update("flag", json(value)));
        verify(repository, never()).save(any());
    }

    private static FeatureFlagEntity entity(String key, FeatureFlagType type, String value) {
        FeatureFlagEntity entity = new FeatureFlagEntity();
        entity.setId(1);
        entity.setKey(key);
        entity.setType(type);
        entity.setValue(json(value));
        return entity;
    }

    private static JsonNode json(String value) {
        try {
            return OBJECT_MAPPER.readTree(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
