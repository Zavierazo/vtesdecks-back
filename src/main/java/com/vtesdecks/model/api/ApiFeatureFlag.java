package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.vtesdecks.enums.FeatureFlagType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiFeatureFlag implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String key;
    private FeatureFlagType type;
    private JsonNode value;
    private String description;
}
