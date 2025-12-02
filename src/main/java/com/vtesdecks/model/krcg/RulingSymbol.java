package com.vtesdecks.model.krcg;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RulingSymbol {
    private String text;
    private String symbol;
}
