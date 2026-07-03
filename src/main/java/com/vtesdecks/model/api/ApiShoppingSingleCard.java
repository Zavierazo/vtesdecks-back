package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiShoppingSingleCard {
    private Integer id;
    private Integer number;
    /** Price of a single copy, null when no shop price is known for the card */
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
}
