package com.vtesdecks.model.dtc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {
    private BigDecimal lowestPrintPrice;
    private Integer productId;
    private ProductDescription description;
}
