package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiCardInfo {
    private List<ApiDeck> preconstructedDecks;
    private List<ApiShop> shopList;
    private List<ApiRuling> rulingList;
    private ApiCollectionCardStats collectionStats;
}
