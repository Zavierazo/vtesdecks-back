package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiCollectionStats {
    private CollectionSectionStats overall = new CollectionSectionStats();
    private CollectionSectionStats crypt = new CollectionSectionStats();
    private CollectionSectionStats library = new CollectionSectionStats();
    private Map<String, CollectionSectionStats> clans = new HashMap<>();
    private Map<String, CollectionSectionStats> types = new HashMap<>();
    private Map<String, CollectionSectionStats> sets = new HashMap<>();
    private String currency;
}
