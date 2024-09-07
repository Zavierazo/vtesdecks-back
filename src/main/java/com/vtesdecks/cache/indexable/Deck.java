package com.vtesdecks.cache.indexable;

import com.googlecode.cqengine.attribute.Attribute;
import com.googlecode.cqengine.attribute.MultiValueNullableAttribute;
import com.googlecode.cqengine.query.QueryFactory;
import com.googlecode.cqengine.query.option.QueryOptions;
import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.cache.indexable.deck.Stats;
import com.vtesdecks.cache.indexable.deck.card.Card;
import com.vtesdecks.model.Errata;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
public class Deck {
    public static final Attribute<Deck, String> ID_ATTRIBUTE = QueryFactory.attribute("id", Deck::getId);
    public static final Attribute<Deck, Boolean> PUBLISHED_ATTRIBUTE = QueryFactory.attribute("published", Deck::isPublished);
    public static final Attribute<Deck, DeckType> TYPE_ATTRIBUTE = QueryFactory.attribute("type", Deck::getType);
    public static final Attribute<Deck, String> NAME_ATTRIBUTE = QueryFactory.attribute("name", deck -> deck.getName().toLowerCase());
    public static final Attribute<Deck, Double> RATE_ATTRIBUTE = QueryFactory.nullableAttribute("score", Deck::getRate);
    public static final Attribute<Deck, Integer> VOTES_ATTRIBUTE = QueryFactory.attribute("votes", Deck::getVotes);
    public static final Attribute<Deck, Long> VIEWS_ATTRIBUTE = QueryFactory.attribute("views", Deck::getViews);
    public static final Attribute<Deck, Integer> PLAYERS_ATTRIBUTE = QueryFactory.nullableAttribute("players", Deck::getPlayers);
    public static final Attribute<Deck, Integer> YEAR_ATTRIBUTE = QueryFactory.nullableAttribute("year", Deck::getYear);
    public static final Attribute<Deck, Long> VIEWS_LAST_MONTH_ATTRIBUTE = QueryFactory.attribute("views_last_month", Deck::getViewsLastMonth);
    public static final Attribute<Deck, Long> COMMENTS_ATTRIBUTE = QueryFactory.attribute("comments", Deck::getComments);
    public static final Attribute<Deck, Integer> CRYPT_SIZE_ATTRIBUTE = QueryFactory.attribute("crypt_size", deck -> deck.getStats().getCrypt());
    public static final Attribute<Deck, Integer> LIBRARY_SIZE_ATTRIBUTE =
            QueryFactory.attribute("library_size", deck -> deck.getStats().getLibrary());
    public static final Attribute<Deck, LocalDateTime> CREATION_DATE_ATTRIBUTE = QueryFactory.attribute("creationDate", Deck::getCreationDate);
    public static final Attribute<Deck, LocalDateTime> MODIFY_DATE_ATTRIBUTE = QueryFactory.attribute("modificationDate", Deck::getModifyDate);
    public static final Attribute<Deck, Integer> USER_ATTRIBUTE = QueryFactory.nullableAttribute("user", Deck::getUser);
    public static final Attribute<Deck, Integer> CLAN_NUMBER_ATTRIBUTE = QueryFactory.nullableAttribute("clanNumber", deck -> deck.getClans().size());
    public static final Attribute<Deck, Integer> DISCIPLINE_NUMBER_ATTRIBUTE =
            QueryFactory.nullableAttribute("disciplineNumber", deck -> deck.getDisciplines().size());
    public static final Attribute<Deck, String> AUTHOR_ATTRIBUTE = QueryFactory.nullableAttribute("author",
            deck -> deck.getAuthor() != null ? deck.getAuthor().toLowerCase() : null);
    public static final Attribute<Deck, String> CLAN_MULTI_ATTRIBUTE = new MultiValueNullableAttribute<Deck, String>(true) {
        public Iterable<String> getNullableValues(Deck deck, QueryOptions queryOptions) {
            return deck.getClans();
        }
    };
    public static final Attribute<Deck, Integer> GROUP_MULTI_ATTRIBUTE = new MultiValueNullableAttribute<Deck, Integer>(true) {
        public Iterable<Integer> getNullableValues(Deck deck, QueryOptions queryOptions) {
            return deck.getGroups();
        }
    };
    public static final Attribute<Deck, String> DISCIPLINE_MULTI_ATTRIBUTE = new MultiValueNullableAttribute<Deck, String>(true) {
        public Iterable<String> getNullableValues(Deck deck, QueryOptions queryOptions) {
            return deck.getDisciplines();
        }
    };
    public static final Attribute<Deck, Integer> MASTER_PERCENTAGE_ATTRIBUTE = QueryFactory.attribute("master_percentage", deck -> deck.getStats()
            .getPercentage(deck.getStats().getMaster()));
    public static final Attribute<Deck, Integer> ACTION_PERCENTAGE_ATTRIBUTE = QueryFactory.attribute("action_percentage", deck -> deck.getStats()
            .getPercentage(deck.getStats().getAction()));
    public static final Attribute<Deck, Integer> POLITICAL_PERCENTAGE_ATTRIBUTE =
            QueryFactory.attribute("political_percentage", deck -> deck.getStats()
                    .getPercentage(deck.getStats().getPoliticalAction()));
    public static final Attribute<Deck, Integer> RETAINER_PERCENTAGE_ATTRIBUTE = QueryFactory.attribute("retainer_percentage", deck -> deck.getStats()
            .getPercentage(deck.getStats().getRetainer()));
    public static final Attribute<Deck, Integer> EQUIPMENT_PERCENTAGE_ATTRIBUTE =
            QueryFactory.attribute("equipment_percentage", deck -> deck.getStats()
                    .getPercentage(deck.getStats().getEquipment()));
    public static final Attribute<Deck, Integer> ALLY_PERCENTAGE_ATTRIBUTE = QueryFactory.attribute("ally_percentage", deck -> deck.getStats()
            .getPercentage(deck.getStats().getAlly()));
    public static final Attribute<Deck, Integer> MODIFIER_PERCENTAGE_ATTRIBUTE = QueryFactory.attribute("modifier_percentage", deck -> deck.getStats()
            .getPercentage(deck.getStats().getActionModifier()));
    public static final Attribute<Deck, Integer> COMBAT_PERCENTAGE_ATTRIBUTE = QueryFactory.attribute("combat_percentage", deck -> deck.getStats()
            .getPercentage(deck.getStats().getCombat()));
    public static final Attribute<Deck, Integer> REACTION_PERCENTAGE_ATTRIBUTE = QueryFactory.attribute("reaction_percentage", deck -> deck.getStats()
            .getPercentage(deck.getStats().getReaction()));
    public static final Attribute<Deck, Integer> EVENT_PERCENTAGE_ATTRIBUTE = QueryFactory.attribute("event_percentage", deck -> deck.getStats()
            .getPercentage(deck.getStats().getEvent()));
    public static final Attribute<Deck, Integer> MASTER_ABSOLUTE_ATTRIBUTE = QueryFactory.attribute("master_absolute", deck -> deck.getStats().getMaster());
    public static final Attribute<Deck, Integer> ACTION_ABSOLUTE_ATTRIBUTE = QueryFactory.attribute("action_absolute", deck -> deck.getStats().getAction());
    public static final Attribute<Deck, Integer> POLITICAL_ABSOLUTE_ATTRIBUTE = QueryFactory.attribute("political_absolute", deck -> deck.getStats().getPoliticalAction());
    public static final Attribute<Deck, Integer> RETAINER_ABSOLUTE_ATTRIBUTE = QueryFactory.attribute("retainer_absolute", deck -> deck.getStats().getRetainer());
    public static final Attribute<Deck, Integer> EQUIPMENT_ABSOLUTE_ATTRIBUTE = QueryFactory.attribute("equipment_absolute", deck -> deck.getStats().getEquipment());
    public static final Attribute<Deck, Integer> ALLY_ABSOLUTE_ATTRIBUTE = QueryFactory.attribute("ally_absolute", deck -> deck.getStats().getAlly());
    public static final Attribute<Deck, Integer> MODIFIER_ABSOLUTE_ATTRIBUTE = QueryFactory.attribute("modifier_absolute", deck -> deck.getStats().getActionModifier());
    public static final Attribute<Deck, Integer> COMBAT_ABSOLUTE_ATTRIBUTE = QueryFactory.attribute("combat_absolute", deck -> deck.getStats().getCombat());
    public static final Attribute<Deck, Integer> REACTION_ABSOLUTE_ATTRIBUTE = QueryFactory.attribute("reaction_absolute", deck -> deck.getStats().getReaction());
    public static final Attribute<Deck, Integer> EVENT_ABSOLUTE_ATTRIBUTE = QueryFactory.attribute("event_absolute", deck -> deck.getStats().getEvent());
    public static final Attribute<Deck, String> TAG_MULTI_ATTRIBUTE = new MultiValueNullableAttribute<Deck, String>(true) {
        public Iterable<String> getNullableValues(Deck deck, QueryOptions queryOptions) {
            return deck.getTags();
        }
    };
    public static final Attribute<Deck, Integer> FAVORITE_MULTI_ATTRIBUTE = new MultiValueNullableAttribute<Deck, Integer>(true) {
        public Iterable<Integer> getNullableValues(Deck deck, QueryOptions queryOptions) {
            return deck.getFavoriteUsers();
        }
    };

    private String id;
    private DeckType type;
    private String name;
    private Long views;
    private Long viewsLastMonth;
    private Set<Integer> favoriteUsers;
    private Double rate;
    private Integer votes;
    private Long comments;
    private String tournament;
    private Integer players;
    private Integer year;
    private Integer user;
    private String author;
    private String url;
    private String source;
    private String description;
    private boolean published;
    private List<Card> crypt = new ArrayList<>();
    private Map<String, List<Card>> libraryByType = new HashMap<>();
    private Set<String> clanIcons;
    private Set<String> disciplineIcons;
    private Set<String> clans;
    private Set<Integer> groups;
    private Set<String> disciplines;
    private Stats stats;
    private List<Errata> erratas;
    private Set<String> tags;
    private LocalDateTime creationDate;
    private LocalDateTime modifyDate;

    public Integer getLibrarySize(String type) {
        List<Card> libraries = libraryByType.get(type);
        return libraries != null ? libraries.stream().mapToInt(Card::getNumber).sum() : 0;
    }

}
