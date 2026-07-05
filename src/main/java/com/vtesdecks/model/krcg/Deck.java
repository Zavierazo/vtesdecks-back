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
public class Deck {
    private String id;
    private String name;
    private String comment;
    private String author;
    private List<DeckCard> cards;
}
