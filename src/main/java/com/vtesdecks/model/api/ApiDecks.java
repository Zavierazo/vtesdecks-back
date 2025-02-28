package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiDecks {
    private Integer offset;
    private Integer total;
    private List<ApiDeck> decks;
    private List<ApiDeck> restorableDecks;
}
