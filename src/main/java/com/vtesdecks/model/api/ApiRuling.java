package com.vtesdecks.model.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiRuling {
    List<ApiRulingReference> references;
    List<ApiRulingSymbol> symbols;
    List<ApiRulingCard> cards;
    String text;
}
