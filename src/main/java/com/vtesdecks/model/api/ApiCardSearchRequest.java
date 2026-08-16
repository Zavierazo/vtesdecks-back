package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiCardSearchRequest {
    private Integer page = 0;
    private Integer size = 20;
    private String sortBy;
    private String sortDirection;
    private Map<String, Object> filters;
    private List<Integer> cardIds;
}
