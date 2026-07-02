package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vtesdecks.enums.WishlistPriority;
import com.vtesdecks.model.CardCondition;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiWishlistCard {
    private Integer id;
    private Integer cardId;
    private String cardName;
    private Integer number;
    private WishlistPriority priority;
    private String set;
    private CardCondition condition;
    private String language;
    private BigDecimal price;
    private BigDecimal totalPrice;
    private String currency;
    private String notes;
    private LocalDateTime creationDate;
    private LocalDateTime modificationDate;
}
