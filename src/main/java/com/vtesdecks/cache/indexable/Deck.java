package com.vtesdecks.cache.indexable;

import com.fasterxml.jackson.databind.JsonNode;
import com.googlecode.cqengine.attribute.Attribute;
import com.googlecode.cqengine.attribute.MultiValueNullableAttribute;
import com.googlecode.cqengine.query.QueryFactory;
import com.googlecode.cqengine.query.option.QueryOptions;
import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.cache.indexable.deck.Stats;
import com.vtesdecks.cache.indexable.deck.card.Card;
import com.vtesdecks.jpa.entity.UserEntity;
import com.vtesdecks.model.Errata;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
public class Deck {
    public static final Attribute<Deck, String> ID_ATTRIBUTE = QueryFactory.attribute(Deck.class, String.class, "id", Deck::getId);
    public static final Attribute<Deck, Boolean> PUBLISHED_ATTRIBUTE = QueryFactory.attribute(Deck.class, Boolean.class, "published", Deck::isPublished);
    public static final Attribute<Deck, DeckType> TYPE_ATTRIBUTE = QueryFactory.attribute(Deck.class, DeckType.class, "type", Deck::getType);
    public static final Attribute<Deck, String> NAME_ATTRIBUTE = QueryFactory.attribute(Deck.class, String.class, "name", (Deck deck) -> deck.getName().toLowerCase());
    public static final Attribute<Deck, Double> RATE_ATTRIBUTE = QueryFactory.nullableAttribute(Deck.class, Double.class, "score", Deck::getRate);
    public static final Attribute<Deck, Integer> VOTES_ATTRIBUTE = QueryFactory.attribute(Deck.class, Integer.class, "votes", Deck::getVotes);
    public static final Attribute<Deck, Long> VIEWS_ATTRIBUTE = QueryFactory.attribute(Deck.class, Long.class, "views", Deck::getViews);
    public static final Attribute<Deck, Integer> PLAYERS_ATTRIBUTE = QueryFactory.nullableAttribute(Deck.class, Integer.class, "players", Deck::getPlayers);
    public static final Attribute<Deck, Integer> YEAR_ATTRIBUTE = QueryFactory.nullableAttribute(Deck.class, Integer.class, "year", Deck::getYear);
    public static final Attribute<Deck, Long> VIEWS_LAST_MONTH_ATTRIBUTE = QueryFactory.attribute(Deck.class, Long.class, "views_last_month", Deck::getViewsLastMonth);
    public static final Attribute<Deck, Long> COMMENTS_ATTRIBUTE = QueryFactory.attribute(Deck.class, Long.class, "comments", Deck::getComments);
    public static final Attribute<Deck, Integer> CRYPT_SIZE_ATTRIBUTE = QueryFactory.attribute(Deck.class, Integer.class, "crypt_size", (Deck deck) -> deck.getStats().getCrypt());
    public static final Attribute<Deck, Integer> LIBRARY_SIZE_ATTRIBUTE = QueryFactory.attribute(Deck.class, Integer.class, "library_size", (Deck deck) -> deck.getStats().getLibrary());
    public static final Attribute<Deck, LocalDateTime> CREATION_DATE_ATTRIBUTE = QueryFactory.attribute(Deck.class, LocalDateTime.class, "creationDate", Deck::getCreationDate);
    public static final Attribute<Deck, LocalDateTime> MODIFY_DATE_ATTRIBUTE = QueryFactory.attribute(Deck.class, LocalDateTime.class, "modificationDate", Deck::getModifyDate);
    public static final Attribute<Deck, Integer> USER_ATTRIBUTE = QueryFactory.nullableAttribute(Deck.class, Integer.class, "user", (Deck deck) -> deck.getUser() != null ? deck.getUser().getId() : null);
    public static final Attribute<Deck, Integer> CLAN_NUMBER_ATTRIBUTE = QueryFactory.nullableAttribute(Deck.class, Integer.class, "clanNumber", (Deck deck) -> deck.getClans().size());
    public static final Attribute<Deck, Integer> DISCIPLINE_NUMBER_ATTRIBUTE = QueryFactory.nullableAttribute(Deck.class, Integer.class, "disciplineNumber", (Deck deck) -> deck.getDisciplines().size());
    public static final Attribute<Deck, String> AUTHOR_ATTRIBUTE = QueryFactory.nullableAttribute(Deck.class, String.class, "author", (Deck deck) -> deck.getAuthor() != null ? deck.getAuthor().toLowerCase() : null);
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
    public static final Attribute<Deck, Integer> MASTER_PERCENTAGE_ATTRIBUTE = QueryFactory.attribute(Deck.class, Integer.class, "master_percentage", (Deck deck) -> deck.getStats().getPercentage(deck.getStats().getMaster()));
    public static final Attribute<Deck, Integer> ACTION_PERCENTAGE_ATTRIBUTE = QueryFactory.attribute(Deck.class, Integer.class, "action_percentage", (Deck deck) -> deck.getStats().getPercentage(deck.getStats().getAction()));
    public static final Attribute<Deck, Integer> POLITICAL_PERCENTAGE_ATTRIBUTE = QueryFactory.attribute(Deck.class, Integer.class, "political_percentage", (Deck deck) -> deck.getStats().getPercentage(deck.getStats().getPoliticalAction()));
    public static final Attribute<Deck, Integer> RETAINER_PERCENTAGE_ATTRIBUTE = QueryFactory.attribute(Deck.class, Integer.class, "retainer_percentage", (Deck deck) -> deck.getStats().getPercentage(deck.getStats().getRetainer()));
    public static final Attribute<Deck, Integer> EQUIPMENT_PERCENTAGE_ATTRIBUTE = QueryFactory.attribute(Deck.class, Integer.class, "equipment_percentage", (Deck deck) -> deck.getStats().getPercentage(deck.getStats().getEquipment()));
    public static final Attribute<Deck, Integer> ALLY_PERCENTAGE_ATTRIBUTE = QueryFactory.attribute(Deck.class, Integer.class, "ally_percentage", (Deck deck) -> deck.getStats().getPercentage(deck.getStats().getAlly()));
    public static final Attribute<Deck, Integer> MODIFIER_PERCENTAGE_ATTRIBUTE = QueryFactory.attribute(Deck.class, Integer.class, "modifier_percentage", (Deck deck) -> deck.getStats().getPercentage(deck.getStats().getActionModifier()));
    public static final Attribute<Deck, Integer> COMBAT_PERCENTAGE_ATTRIBUTE = QueryFactory.attribute(Deck.class, Integer.class, "combat_percentage", (Deck deck) -> deck.getStats().getPercentage(deck.getStats().getCombat()));
    public static final Attribute<Deck, Integer> REACTION_PERCENTAGE_ATTRIBUTE = QueryFactory.attribute(Deck.class, Integer.class, "reaction_percentage", (Deck deck) -> deck.getStats().getPercentage(deck.getStats().getReaction()));
    public static final Attribute<Deck, Integer> EVENT_PERCENTAGE_ATTRIBUTE = QueryFactory.attribute(Deck.class, Integer.class, "event_percentage", (Deck deck) -> deck.getStats().getPercentage(deck.getStats().getEvent()));
    public static final Attribute<Deck, Integer> MASTER_ABSOLUTE_ATTRIBUTE = QueryFactory.attribute(Deck.class, Integer.class, "master_absolute", (Deck deck) -> deck.getStats().getMaster());
    public static final Attribute<Deck, Integer> ACTION_ABSOLUTE_ATTRIBUTE = QueryFactory.attribute(Deck.class, Integer.class, "action_absolute", (Deck deck) -> deck.getStats().getAction());
    public static final Attribute<Deck, Integer> POLITICAL_ABSOLUTE_ATTRIBUTE = QueryFactory.attribute(Deck.class, Integer.class, "political_absolute", (Deck deck) -> deck.getStats().getPoliticalAction());
    public static final Attribute<Deck, Integer> RETAINER_ABSOLUTE_ATTRIBUTE = QueryFactory.attribute(Deck.class, Integer.class, "retainer_absolute", (Deck deck) -> deck.getStats().getRetainer());
    public static final Attribute<Deck, Integer> EQUIPMENT_ABSOLUTE_ATTRIBUTE = QueryFactory.attribute(Deck.class, Integer.class, "equipment_absolute", (Deck deck) -> deck.getStats().getEquipment());
    public static final Attribute<Deck, Integer> ALLY_ABSOLUTE_ATTRIBUTE = QueryFactory.attribute(Deck.class, Integer.class, "ally_absolute", (Deck deck) -> deck.getStats().getAlly());
    public static final Attribute<Deck, Integer> MODIFIER_ABSOLUTE_ATTRIBUTE = QueryFactory.attribute(Deck.class, Integer.class, "modifier_absolute", (Deck deck) -> deck.getStats().getActionModifier());
    public static final Attribute<Deck, Integer> COMBAT_ABSOLUTE_ATTRIBUTE = QueryFactory.attribute(Deck.class, Integer.class, "combat_absolute", (Deck deck) -> deck.getStats().getCombat());
    public static final Attribute<Deck, Integer> REACTION_ABSOLUTE_ATTRIBUTE = QueryFactory.attribute(Deck.class, Integer.class, "reaction_absolute", (Deck deck) -> deck.getStats().getReaction());
    public static final Attribute<Deck, Integer> EVENT_ABSOLUTE_ATTRIBUTE = QueryFactory.attribute(Deck.class, Integer.class, "event_absolute", (Deck deck) -> deck.getStats().getEvent());
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
    public static final Attribute<Deck, String> LIMITED_FORMAT_ATTRIBUTE = QueryFactory.nullableAttribute(Deck.class, String.class, "limited_format", (Deck deck) -> StringUtils.lowerCase(deck.getLimitedFormat()));
    public static final Attribute<Deck, String> PATH_ATTRIBUTE = QueryFactory.nullableAttribute(Deck.class, String.class, "path", Deck::getPath);

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
    private UserEntity user;
    private List<String> userRoles;
    private String author;
    private String url;
    private String source;
    private String description;
    private String set;
    private String limitedFormat;
    private JsonNode extra;
    private boolean published;
    private boolean collection;
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
    private Double l2Norm;
    private String path;
    private String pathIcon;
    private LocalDateTime creationDate;
    private LocalDateTime modifyDate;

    public Integer getLibrarySize(String type) {
        List<Card> libraries = libraryByType.get(type);
        return libraries != null ? libraries.stream().mapToInt(Card::getNumber).sum() : 0;
    }

}
