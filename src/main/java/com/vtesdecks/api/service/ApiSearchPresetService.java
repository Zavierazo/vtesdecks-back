package com.vtesdecks.api.service;

import com.vtesdecks.api.mapper.ApiSearchPresetMapper;
import com.vtesdecks.api.util.ApiUtils;
import com.vtesdecks.jpa.entity.UserSearchPresetEntity;
import com.vtesdecks.jpa.repositories.UserSearchPresetRepository;
import com.vtesdecks.model.api.ApiSearchPreset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ApiSearchPresetService {
    static final int MAX_PRESETS_PER_USER = 100;
    static final int MAX_NAME_LENGTH = 64;
    static final int MAX_PARAMS = 50;
    static final int MAX_PARAM_KEY = 64;
    static final int MAX_PARAM_VALUE = 512;

    private final UserSearchPresetRepository repository;
    private final ApiSearchPresetMapper mapper;

    public List<ApiSearchPreset> getPresets() throws Exception {
        try {
            Integer userId = ApiUtils.extractUserId();
            return getPresets(userId);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new Exception("An unexpected error occurred while retrieving search presets", e);
        }
    }

    public ApiSearchPreset createPreset(ApiSearchPreset preset) throws Exception {
        try {
            Integer userId = ApiUtils.extractUserId();
            validate(preset);
            ensureNameAvailable(userId, preset, null);
            if (repository.countByUserId(userId) >= MAX_PRESETS_PER_USER) {
                throw new IllegalArgumentException("Maximum number of presets reached");
            }
            UserSearchPresetEntity entity = mapper.mapToEntity(preset);
            entity.setUserId(userId);
            entity.setName(preset.getName().trim());
            return mapper.mapToApi(repository.save(entity));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new Exception("An unexpected error occurred while creating the search preset", e);
        }
    }

    public ApiSearchPreset updatePreset(Integer id, ApiSearchPreset preset) throws Exception {
        try {
            Integer userId = ApiUtils.extractUserId();
            UserSearchPresetEntity existing = repository.findByUserIdAndId(userId, id)
                    .orElseThrow(() -> new IllegalArgumentException("Preset does not exist"));
            ApiSearchPreset updated = ApiSearchPreset.builder()
                    .scope(existing.getScope())
                    .name(preset.getName() != null ? preset.getName() : existing.getName())
                    .params(preset.getParams() != null ? preset.getParams() : mapper.mapToApi(existing).getParams())
                    .build();
            validate(updated);
            ensureNameAvailable(userId, updated, id);
            existing.setScope(updated.getScope());
            existing.setName(updated.getName().trim());
            existing.setParams(mapper.mapToEntity(updated).getParams());
            return mapper.mapToApi(repository.save(existing));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new Exception("An unexpected error occurred while updating the search preset", e);
        }
    }

    public Boolean deletePreset(Integer id) throws Exception {
        try {
            Integer userId = ApiUtils.extractUserId();
            if (repository.deleteByUserIdAndId(userId, id) == 0) {
                throw new IllegalArgumentException("Preset does not exist");
            }
            return true;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new Exception("An unexpected error occurred while deleting the search preset", e);
        }
    }

    public List<ApiSearchPreset> mergePresets(List<ApiSearchPreset> presets) throws Exception {
        try {
            Integer userId = ApiUtils.extractUserId();
            int count = repository.countByUserId(userId);
            if (presets != null) {
                for (ApiSearchPreset preset : presets) {
                    validate(preset);
                    if (count >= MAX_PRESETS_PER_USER) {
                        break;
                    }
                    if (preset.getClientId() != null && repository.findByUserIdAndClientId(userId, preset.getClientId()).isPresent()) {
                        continue;
                    }
                    if (repository.findByUserIdAndScopeAndName(userId, preset.getScope(), preset.getName().trim()).isPresent()) {
                        continue;
                    }
                    UserSearchPresetEntity entity = mapper.mapToEntity(preset);
                    entity.setUserId(userId);
                    entity.setName(preset.getName().trim());
                    repository.save(entity);
                    count++;
                }
            }
            return getPresets(userId);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new Exception("An unexpected error occurred while merging search presets", e);
        }
    }

    private List<ApiSearchPreset> getPresets(Integer userId) {
        return repository.findByUserIdOrderByScopeAscNameAsc(userId).stream()
                .map(mapper::mapToApi)
                .toList();
    }

    private void ensureNameAvailable(Integer userId, ApiSearchPreset preset, Integer currentId) {
        repository.findByUserIdAndScopeAndName(userId, preset.getScope(), preset.getName().trim())
                .filter(entity -> !entity.getId().equals(currentId))
                .ifPresent(entity -> {
                    throw new IllegalArgumentException("A preset with this name already exists");
                });
    }

    private void validate(ApiSearchPreset preset) {
        if (preset == null || preset.getScope() == null) {
            throw new IllegalArgumentException("Preset scope cannot be empty");
        }
        if (preset.getName() == null || preset.getName().isBlank()) {
            throw new IllegalArgumentException("Preset name cannot be empty");
        }
        if (preset.getName().trim().length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Preset name is too long");
        }
        Map<String, String> params = preset.getParams();
        if (params == null) {
            throw new IllegalArgumentException("Preset params cannot be empty");
        }
        if (params.size() > MAX_PARAMS) {
            throw new IllegalArgumentException("Preset has too many params");
        }
        params.forEach((key, value) -> {
            if (key == null || key.length() > MAX_PARAM_KEY) {
                throw new IllegalArgumentException("Preset param key is invalid");
            }
            if (value == null || value.length() > MAX_PARAM_VALUE) {
                throw new IllegalArgumentException("Preset param value is invalid");
            }
        });
    }
}
