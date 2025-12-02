package com.vtesdecks.model.krcg;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ruling {
    List<RulingReference> references;
    List<RulingSymbol> symbols;
    List<RulingCard> cards;
    String text;
}
