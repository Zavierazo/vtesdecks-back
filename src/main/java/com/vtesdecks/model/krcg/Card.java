package com.vtesdecks.model.krcg;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Card {
    private Integer id;
    private String name;
    private Integer count;
    private String type;
    private List<Card> cards;
}
