package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vtesdecks.enums.SearchPresetScope;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiSearchPreset {
    private Integer id;
    private String clientId;
    private SearchPresetScope scope;
    private String name;
    private Map<String, String> params;
    private LocalDateTime creationDate;
    private LocalDateTime modificationDate;
}
