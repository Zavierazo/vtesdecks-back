package com.vtesdecks.db.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.vtesdecks.cache.indexable.deck.DeckType;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DbDeck extends DbBase {
    private static final long serialVersionUID = 1L;

    private String id;
    private DeckType type;
    private Integer user;
    private String tournament;
    private Integer players;
    private Integer year;
    private String author;
    private String url;
    private String source;
    private String name;
    private String description;
    private JsonNode extra;
    private long views = 0;
    private boolean verified = false;
    private boolean published = true;
    private boolean deleted = false;
}
