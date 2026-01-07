package com.vtesdecks.model;

import com.vtesdecks.cache.indexable.deck.DeckType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeckQuery {
    private DeckType type;
    @Builder.Default
    private DeckSort order = DeckSort.NEWEST;
    private Integer userId;
    private String name;
    private String author;
    private String username;
    @Deprecated(since = "Frontend 2.62.0", forRemoval = true)
    private Boolean exactAuthor;
    private String cardText;
    private List<String> clans;
    private List<String> disciplines;
    private Map<Integer, Integer> cards;
    private Integer cryptSizeMin;
    private Integer cryptSizeMax;
    private Integer librarySizeMin;
    private Integer librarySizeMax;
    private List<Integer> group;
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
    private String limitedFormat;
    private List<String> paths;
    private Boolean favorite;
    private Integer archetype;
    private LocalDate creationDate;

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
    @Slf4j
    public static class CardProportion {
        private Integer min;
        private Integer max;

        public static CardProportion fromValue(String value) {
            if (value == null || value.isEmpty()) {
                return null;
            }
            try {
                String[] percentageSplit = value.split(",");
                return new CardProportion(Integer.parseInt(percentageSplit[0]), Integer.parseInt(percentageSplit[1]));
            } catch (Exception e) {
                log.error("Unable to parse percentage with value {}", value, e);
                return null;
            }
        }


    }

    public enum ProportionType {
        PERCENTAGE,
        ABSOLUTE
    }

    @Slf4j
    public static class DeckQueryBuilder {

        public DeckQueryBuilder cards(List<String> cards) {
            Map<Integer, Integer> cardMap = new HashMap<>();
            if (cards != null && !cards.isEmpty()) {
                for (String card : cards) {
                    int indexEqual = card.indexOf('=');
                    int number = 1;
                    int id;
                    if (indexEqual > 0) {
                        id = Integer.parseInt(card.substring(0, indexEqual));
                        number = Integer.parseInt(card.substring(indexEqual + 1));
                    } else {
                        id = Integer.parseInt(card);
                    }
                    cardMap.put(id, number);
                }
            }
            this.cards = cardMap;
            return this;
        }

        public DeckQueryBuilder cryptSize(List<Integer> cryptSize) {
            if (cryptSize != null && cryptSize.size() == 2) {
                Integer cryptSizeMin = cryptSize.get(0);
                if (cryptSizeMin <= 12) {
                    cryptSizeMin = null;
                }
                Integer cryptSizeMax = cryptSize.get(1);
                if (cryptSizeMax >= 40) {
                    cryptSizeMax = null;
                }
                this.cryptSizeMin = cryptSizeMin;
                this.cryptSizeMax = cryptSizeMax;
            }
            return this;
        }

        public DeckQueryBuilder librarySize(List<Integer> librarySize) {
            if (librarySize != null && librarySize.size() == 2) {
                Integer librarySizeMin = librarySize.get(0);
                if (librarySizeMin <= 60) {
                    librarySizeMin = null;
                }
                Integer librarySizeMax = librarySize.get(1);
                if (librarySizeMax >= 90) {
                    librarySizeMax = null;
                }
                this.librarySizeMin = librarySizeMin;
                this.librarySizeMax = librarySizeMax;
            }
            return this;
        }

        public DeckQueryBuilder apiType(ApiDeckType type) {
            if (type != null && type != ApiDeckType.ALL) {
                this.type = DeckType.valueOf(type.name());
            }
            return this;
        }

        public DeckQueryBuilder group(List<Integer> group) {
            if (group != null && group.size() == 2) {
                int minGroup = group.get(0);
                int maxGroup = group.get(1);
                List<Integer> groups = new ArrayList<>();
                if (minGroup == 0) {
                    groups.add(-1);
                    minGroup++;
                }
                for (int i = minGroup; i <= maxGroup; i++) {
                    groups.add(i);
                }
                this.group = groups;
            }
            return this;
        }

        public DeckQueryBuilder year(List<Integer> year) {
            if (year != null && year.size() == 2) {
                Integer minYear = year.get(0);
                if (minYear <= 1998) {
                    minYear = null;
                }
                Integer maxYear = year.get(1);
                if (maxYear >= LocalDate.now().getYear()) {
                    maxYear = null;
                }
                this.minYear = minYear;
                this.maxYear = maxYear;
            }
            return this;
        }

        public DeckQueryBuilder players(List<Integer> players) {
            if (players != null && players.size() == 2) {
                Integer minPlayers = players.get(0);
                if (minPlayers <= 10) {
                    minPlayers = null;
                }
                Integer maxPlayers = players.get(1);
                if (maxPlayers >= 200) {
                    maxPlayers = null;
                }
                this.minPlayers = minPlayers;
                this.maxPlayers = maxPlayers;
            }
            return this;
        }

        public DeckQueryBuilder absoluteProportion(Boolean absoluteProportion) {
            if (absoluteProportion != null && absoluteProportion) {
                this.proportionType = ProportionType.ABSOLUTE;
            } else {
                this.proportionType = ProportionType.PERCENTAGE;
            }
            return this;
        }

        public DeckQueryBuilder master(DeckQuery.CardProportion master) {
            if (master != null) {
                this.master = master;
            }
            return this;
        }

        public DeckQueryBuilder action(DeckQuery.CardProportion action) {
            if (action != null) {
                this.action = action;
            }
            return this;
        }

        public DeckQueryBuilder political(DeckQuery.CardProportion political) {
            if (political != null) {
                this.political = political;
            }
            return this;
        }

        public DeckQueryBuilder retainer(DeckQuery.CardProportion retainer) {
            if (retainer != null) {
                this.retainer = retainer;
            }
            return this;
        }

        public DeckQueryBuilder equipment(DeckQuery.CardProportion equipment) {
            if (equipment != null) {
                this.equipment = equipment;
            }
            return this;
        }

        public DeckQueryBuilder ally(DeckQuery.CardProportion ally) {
            if (ally != null) {
                this.ally = ally;
            }
            return this;
        }

        public DeckQueryBuilder modifier(DeckQuery.CardProportion modifier) {
            if (modifier != null) {
                this.modifier = modifier;
            }
            return this;
        }

        public DeckQueryBuilder combat(DeckQuery.CardProportion combat) {
            if (combat != null) {
                this.combat = combat;
            }
            return this;
        }

        public DeckQueryBuilder reaction(DeckQuery.CardProportion reaction) {
            if (reaction != null) {
                this.reaction = reaction;
            }
            return this;
        }

        public DeckQueryBuilder event(DeckQuery.CardProportion event) {
            if (event != null) {
                this.event = event;
            }
            return this;
        }


    }
}
