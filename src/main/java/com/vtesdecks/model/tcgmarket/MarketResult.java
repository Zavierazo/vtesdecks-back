package com.vtesdecks.model.tcgmarket;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketResult {
    private Long id;
    private Long productId;
    @JsonProperty("card_name")
    private String cardName;
    @JsonProperty("min_price")
    private BigDecimal minPrice;
    private Integer vendors;
    private MarketProperty edition;
    private MarketProperty language;
}
