package com.vtesdecks.model.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiDecks {
    private Integer offset;
    private Integer total;
    private List<ApiDeck> decks;
}
