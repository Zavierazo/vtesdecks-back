package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.math.BigDecimal;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiShop {
    private Integer cardId;
    private ApiShopInfo shopInfo;
    private String set;
    private String locale;
    private String link;
    private BigDecimal price;
    private String currency;
    private boolean inStock;
    private Integer stockQuantity;
}
