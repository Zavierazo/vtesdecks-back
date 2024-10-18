package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.math.BigDecimal;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiStatistic {
    private String label;
    private Integer count = 0;
    private BigDecimal percentage = BigDecimal.ZERO;
}
