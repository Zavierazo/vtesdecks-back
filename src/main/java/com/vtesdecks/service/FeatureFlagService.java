package com.vtesdecks.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.vtesdecks.api.mapper.ApiFeatureFlagMapper;
import com.vtesdecks.enums.FeatureFlagType;
import com.vtesdecks.jpa.entity.FeatureFlagEntity;
import com.vtesdecks.jpa.repositories.FeatureFlagRepository;
import com.vtesdecks.model.api.ApiFeatureFlag;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class FeatureFlagService {
    private final FeatureFlagRepository repository;
    private final ApiFeatureFlagMapper mapper;

    @Cacheable("featureFlags")
    public List<ApiFeatureFlag> getAll() {
        return mapper.map(repository.findAll());
    }

    @CacheEvict(value = "featureFlags", allEntries = true)
    public Optional<ApiFeatureFlag> update(String key, JsonNode value) {
        Optional<FeatureFlagEntity> entity = repository.findByKey(key);
        if (entity.isEmpty()) {
            return Optional.empty();
        }
        validateValue(entity.get().getType(), value);
        entity.get().setValue(value);
        return Optional.of(mapper.map(repository.save(entity.get())));
    }

    private void validateValue(FeatureFlagType type, JsonNode value) {
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("Feature flag value is required");
        }
        boolean valid = switch (type) {
            case BOOLEAN -> value.isBoolean();
            case STRING -> value.isTextual();
            case LIST -> value.isArray() && StreamSupport.stream(value.spliterator(), false).allMatch(JsonNode::isTextual);
        };
        if (!valid) {
            throw new IllegalArgumentException("Feature flag value does not match type " + type);
        }
    }
}
