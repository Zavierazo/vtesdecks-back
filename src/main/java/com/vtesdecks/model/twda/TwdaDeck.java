package com.vtesdecks.model.twda;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * A tournament winning deck of the <a href="https://static.krcg.org/data/v5/twda.json">KRCG TWDA
 * v5</a> archive. {@code id} matches the anchor id of the official {@code vekn.fr/decks/twd.htm}
 * archive for legacy decks and is a UUID for decks reported through the new archon site.
 * {@code player} is the tournament winner; {@code author} is only present when the deck was
 * created by someone else.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TwdaDeck {
    private String id;
    private String name;
    private String comment;
    private String author;
    private String player;
    private TwdaEvent event;
    private List<TwdaCard> cards = new ArrayList<>();
}
