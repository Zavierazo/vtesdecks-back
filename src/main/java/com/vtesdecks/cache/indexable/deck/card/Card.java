package com.vtesdecks.cache.indexable.deck.card;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Card {
    private Integer id;
    private Integer number;
}
