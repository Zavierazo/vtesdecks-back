package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.math.BigDecimal;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiShop {
    private Integer cardId;
    private String platform;
    private String set;
    private String link;
    private BigDecimal price;
}
