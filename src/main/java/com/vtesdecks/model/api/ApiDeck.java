package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.vtesdecks.cache.indexable.deck.DeckType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiDeck {
    private String id;
    private DeckType type;
    private String name;
    private Long views;
    private Double rate;
    private Integer votes;
    private Long comments;
    private String tournament;
    private Integer players;
    private Integer year;
    private String author;
    private ApiUser user;
    private String url;
    private String source;
    private String description;
    private String set;
    private String limitedFormat;
    private JsonNode extra;
    private Boolean published;
    private Boolean collection;
    private List<ApiCard> crypt;
    private List<ApiCard> library;
    private List<ApiCard> filterCards;
    private Set<String> clanIcons;
    private Set<String> disciplineIcons;
    private String pathIcon;
    private ApiDeckStats stats;
    private Boolean favorite = false;
    private Boolean rated = false;
    private Boolean owner = false;
    private Set<ApiErrata> erratas;
    private Set<ApiDeckWarning> warnings;
    private List<String> tags;
    private LocalDateTime creationDate;
    private LocalDateTime modifyDate;
}
