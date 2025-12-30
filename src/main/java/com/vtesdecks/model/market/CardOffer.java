package com.vtesdecks.model.market;

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
public class CardOffer {
    private Integer id;
    @JsonProperty("card_id")
    private Integer cardId;
    private String market;
    private String language;
    private String edition;
    @JsonProperty("edition_details")
    private String editionDetails;
    private String link;
    private BigDecimal price;
    private String currency;
}
