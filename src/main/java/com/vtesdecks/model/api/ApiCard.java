package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vtesdecks.cache.indexable.deck.CollectionTracker;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiCard {
    private Integer id;
    private Integer number;
    private String type;
    private CollectionTracker collection;
}
