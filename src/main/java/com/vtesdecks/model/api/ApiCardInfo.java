package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiCardInfo {
    private List<ApiDeck> preconstructedDecks;
    private List<ApiShop> shopList;
    private Boolean hasMoreShops;
    private List<ApiRuling> rulingList;
    private ApiCollectionCardStats collectionStats;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private String currency;
}
