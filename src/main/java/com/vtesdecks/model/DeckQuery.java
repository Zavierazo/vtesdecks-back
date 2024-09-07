package com.vtesdecks.model;

import com.vtesdecks.cache.indexable.deck.DeckType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeckQuery {
    private DeckType type;
    private DeckSort order;
    private Integer user;
    private String name;
    private String author;
    private String cardText;
    private List<String> clans;
    private List<String> disciplines;
    private Map<Integer, Integer> cards;
    private Integer cryptSizeMin;
    private Integer cryptSizeMax;
    private Integer librarySizeMin;
    private Integer librarySizeMax;
    private List<Integer> groups;
    private Boolean starVampire;
    private Boolean singleClan;
    private Boolean singleDiscipline;
    private Integer minYear;
    private Integer maxYear;
    private Integer minPlayers;
    private Integer maxPlayers;
    private ProportionType proportionType;
    private CardProportion master;
    private CardProportion action;
    private CardProportion political;
    private CardProportion retainer;
    private CardProportion equipment;
    private CardProportion ally;
    private CardProportion modifier;
    private CardProportion combat;
    private CardProportion reaction;
    private CardProportion event;
    private List<String> tags;
    private Boolean favorite;

    public boolean isStarVampire() {
        return starVampire != null && starVampire;
    }

    public boolean isSingleClan() {
        return singleClan != null && singleClan;
    }

    public boolean isSingleDiscipline() {
        return singleDiscipline != null && singleDiscipline;
    }

    public boolean isFavorite() {
        return favorite != null && favorite;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class CardProportion {
        private Integer min;
        private Integer max;
    }

    public enum ProportionType {
        PERCENTAGE,
        ABSOLUTE
    }
}
