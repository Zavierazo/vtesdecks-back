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
public class ApiShoppingPrecon {
    private String deckId;
    private String name;
    private String set;
    private Integer number;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
    /** Requested cards covered by buying this precon (accumulated over all copies) */
    private List<ApiCard> coveredCards;
}
