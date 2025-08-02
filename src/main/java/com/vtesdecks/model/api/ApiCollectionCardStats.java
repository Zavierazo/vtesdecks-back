package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiCollectionCardStats {
    private Integer collectionNumber;
    private Integer decksNumber;
    private Integer trackedDecksNumber;
    private ApiDecks decks;
    private List<ApiCollectionCard> collectionCards;
}
