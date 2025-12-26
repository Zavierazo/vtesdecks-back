package com.vtesdecks.model.tcgmarket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketEdition {
    private Integer id;
    private String code;
    private String name;
}
