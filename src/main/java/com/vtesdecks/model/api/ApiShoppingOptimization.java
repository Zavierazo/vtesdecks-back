package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiShoppingOptimization {
    private List<ApiShoppingPrecon> preconDecks;
    private List<ApiShoppingSingleCard> singleCards;
    /** Optimized cost: precons + priced singles. Cards without a known price are excluded */
    private BigDecimal totalPrice;
    /** Baseline cost of buying every priced card as a single, for comparison */
    private BigDecimal singlesOnlyPrice;
    private String currency;
}
