package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.math.BigDecimal;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiShop {
    private Integer cardId;
    //TODO: delete when front does not use it anymore
    @Deprecated
    private String platform;
    private ApiShopInfo shopInfo;
    private String set;
    private String link;
    private BigDecimal price;
    private String currency;
    private boolean inStock;
    private Integer stockQuantity;
}
