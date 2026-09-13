package com.vtesdecks.cache.indexable;

import com.fasterxml.jackson.databind.JsonNode;
import com.googlecode.cqengine.attribute.Attribute;
import com.googlecode.cqengine.attribute.MultiValueNullableAttribute;
import com.googlecode.cqengine.query.QueryFactory;
import com.googlecode.cqengine.query.option.QueryOptions;
import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.cache.indexable.deck.DeckUser;
import com.vtesdecks.cache.indexable.deck.SummaryStats;
import com.vtesdecks.enums.ReactionType;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.annotation.Transient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;

/** Resident search and listing data; never retains a full deck or card lists. */
@Data
public class DeckSummary {
    public static final Attribute<DeckSummary, String> ID_ATTRIBUTE = QueryFactory.attribute(DeckSummary.class, String.class, "id", DeckSummary::getId);
    public static final Attribute<DeckSummary, Boolean> PUBLISHED_ATTRIBUTE = QueryFactory.attribute(DeckSummary.class, Boolean.class, "published", DeckSummary::isPublished);
    public static final Attribute<DeckSummary, DeckType> TYPE_ATTRIBUTE = QueryFactory.attribute(DeckSummary.class, DeckType.class, "type", DeckSummary::getType);
    public static final Attribute<DeckSummary, String> NAME_ATTRIBUTE = QueryFactory.attribute(DeckSummary.class, String.class, "name", (DeckSummary deck) -> deck.getName().toLowerCase());
    public static final Attribute<DeckSummary, Double> RATE_ATTRIBUTE = QueryFactory.nullableAttribute(DeckSummary.class, Double.class, "score", DeckSummary::getRate);
    public static final Attribute<DeckSummary, Integer> VOTES_ATTRIBUTE = QueryFactory.attribute(DeckSummary.class, Integer.class, "votes", DeckSummary::getVotes);
    public static final Attribute<DeckSummary, Long> VIEWS_ATTRIBUTE = QueryFactory.attribute(DeckSummary.class, Long.class, "views", DeckSummary::getViews);
    public static final Attribute<DeckSummary, Integer> PLAYERS_ATTRIBUTE = QueryFactory.nullableAttribute(DeckSummary.class, Integer.class, "players", DeckSummary::getPlayers);
    public static final Attribute<DeckSummary, Integer> YEAR_ATTRIBUTE = QueryFactory.nullableAttribute(DeckSummary.class, Integer.class, "year", DeckSummary::getYear);
    public static final Attribute<DeckSummary, Long> VIEWS_LAST_MONTH_ATTRIBUTE = QueryFactory.attribute(DeckSummary.class, Long.class, "views_last_month", DeckSummary::getViewsLastMonth);
    public static final Attribute<DeckSummary, Long> COMMENTS_ATTRIBUTE = QueryFactory.attribute(DeckSummary.class, Long.class, "comments", DeckSummary::getComments);
    public static final Attribute<DeckSummary, Integer> CRYPT_SIZE_ATTRIBUTE = QueryFactory.attribute(DeckSummary.class, Integer.class, "crypt_size", (DeckSummary deck) -> deck.getStats().getCrypt());
    public static final Attribute<DeckSummary, Integer> LIBRARY_SIZE_ATTRIBUTE = QueryFactory.attribute(DeckSummary.class, Integer.class, "library_size", (DeckSummary deck) -> deck.getStats().getLibrary());
    public static final Attribute<DeckSummary, LocalDateTime> CREATION_DATE_ATTRIBUTE = QueryFactory.attribute(DeckSummary.class, LocalDateTime.class, "creationDate", DeckSummary::getCreationDate);
    public static final Attribute<DeckSummary, Long> CREATION_TIMESTAMP_ATTRIBUTE = QueryFactory.attribute(DeckSummary.class, Long.class, "creationDate", (DeckSummary deck) -> deck.getCreationDate().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
    public static final Attribute<DeckSummary, LocalDateTime> MODIFY_DATE_ATTRIBUTE = QueryFactory.attribute(DeckSummary.class, LocalDateTime.class, "modificationDate", DeckSummary::getModifyDate);
    public static final Attribute<DeckSummary, Integer> USER_ATTRIBUTE = QueryFactory.nullableAttribute(DeckSummary.class, Integer.class, "user", (DeckSummary deck) -> deck.getUser() != null ? deck.getUser().getId() : null);
    public static final Attribute<DeckSummary, Integer> CLAN_NUMBER_ATTRIBUTE = QueryFactory.nullableAttribute(DeckSummary.class, Integer.class, "clanNumber", (DeckSummary deck) -> deck.getClans().size());
    public static final Attribute<DeckSummary, Integer> DISCIPLINE_NUMBER_ATTRIBUTE = QueryFactory.nullableAttribute(DeckSummary.class, Integer.class, "disciplineNumber", (DeckSummary deck) -> deck.getDisciplines().size());
    public static final Attribute<DeckSummary, String> AUTHOR_ATTRIBUTE = QueryFactory.nullableAttribute(DeckSummary.class, String.class, "author", (DeckSummary deck) -> deck.getAuthor() != null ? deck.getAuthor().toLowerCase() : null);
    public static final Attribute<DeckSummary, String> TOURNAMENT_ATTRIBUTE = QueryFactory.nullableAttribute(DeckSummary.class, String.class, "tournament", (DeckSummary deck) -> StringUtils.lowerCase(deck.getTournament()));
    public static final Attribute<DeckSummary, Integer> ROUNDS_ATTRIBUTE = QueryFactory.nullableAttribute(DeckSummary.class, Integer.class, "rounds", DeckSummary::getRounds);
    public static final Attribute<DeckSummary, String> PLACE_ATTRIBUTE = QueryFactory.nullableAttribute(DeckSummary.class, String.class, "place", (DeckSummary deck) -> StringUtils.lowerCase(deck.getPlace()));
    public static final Attribute<DeckSummary, String> COUNTRY_ATTRIBUTE = QueryFactory.nullableAttribute(DeckSummary.class, String.class, "country", (DeckSummary deck) -> StringUtils.lowerCase(deck.getCountry()));
    public static final Attribute<DeckSummary, String> CLAN_MULTI_ATTRIBUTE = new MultiValueNullableAttribute<DeckSummary, String>(true) {
        public Iterable<String> getNullableValues(DeckSummary deck, QueryOptions queryOptions) {
            return deck.getClans();
        }
    };
    public static final Attribute<DeckSummary, Integer> GROUP_MULTI_ATTRIBUTE = new MultiValueNullableAttribute<DeckSummary, Integer>(true) {
        public Iterable<Integer> getNullableValues(DeckSummary deck, QueryOptions queryOptions) {
            return deck.getGroups();
        }
    };
    public static final Attribute<DeckSummary, String> DISCIPLINE_MULTI_ATTRIBUTE = new MultiValueNullableAttribute<DeckSummary, String>(true) {
        public Iterable<String> getNullableValues(DeckSummary deck, QueryOptions queryOptions) {
            return deck.getDisciplines();
        }
    };
    public static final Attribute<DeckSummary, Integer> MASTER_PERCENTAGE_ATTRIBUTE = QueryFactory.attribute(DeckSummary.class, Integer.class, "master_percentage", (DeckSummary deck) -> deck.getStats().getPercentage(deck.getStats().getMaster()));
    public static final Attribute<DeckSummary, Integer> ACTION_PERCENTAGE_ATTRIBUTE = QueryFactory.attribute(DeckSummary.class, Integer.class, "action_percentage", (DeckSummary deck) -> deck.getStats().getPercentage(deck.getStats().getAction()));
    public static final Attribute<DeckSummary, Integer> POLITICAL_PERCENTAGE_ATTRIBUTE = QueryFactory.attribute(DeckSummary.class, Integer.class, "political_percentage", (DeckSummary deck) -> deck.getStats().getPercentage(deck.getStats().getPoliticalAction()));
    public static final Attribute<DeckSummary, Integer> RETAINER_PERCENTAGE_ATTRIBUTE = QueryFactory.attribute(DeckSummary.class, Integer.class, "retainer_percentage", (DeckSummary deck) -> deck.getStats().getPercentage(deck.getStats().getRetainer()));
    public static final Attribute<DeckSummary, Integer> EQUIPMENT_PERCENTAGE_ATTRIBUTE = QueryFactory.attribute(DeckSummary.class, Integer.class, "equipment_percentage", (DeckSummary deck) -> deck.getStats().getPercentage(deck.getStats().getEquipment()));
    public static final Attribute<DeckSummary, Integer> ALLY_PERCENTAGE_ATTRIBUTE = QueryFactory.attribute(DeckSummary.class, Integer.class, "ally_percentage", (DeckSummary deck) -> deck.getStats().getPercentage(deck.getStats().getAlly()));
    public static final Attribute<DeckSummary, Integer> MODIFIER_PERCENTAGE_ATTRIBUTE = QueryFactory.attribute(DeckSummary.class, Integer.class, "modifier_percentage", (DeckSummary deck) -> deck.getStats().getPercentage(deck.getStats().getActionModifier()));
    public static final Attribute<DeckSummary, Integer> COMBAT_PERCENTAGE_ATTRIBUTE = QueryFactory.attribute(DeckSummary.class, Integer.class, "combat_percentage", (DeckSummary deck) -> deck.getStats().getPercentage(deck.getStats().getCombat()));
    public static final Attribute<DeckSummary, Integer> REACTION_PERCENTAGE_ATTRIBUTE = QueryFactory.attribute(DeckSummary.class, Integer.class, "reaction_percentage", (DeckSummary deck) -> deck.getStats().getPercentage(deck.getStats().getReaction()));
    public static final Attribute<DeckSummary, Integer> EVENT_PERCENTAGE_ATTRIBUTE = QueryFactory.attribute(DeckSummary.class, Integer.class, "event_percentage", (DeckSummary deck) -> deck.getStats().getPercentage(deck.getStats().getEvent()));
    public static final Attribute<DeckSummary, Integer> MASTER_ABSOLUTE_ATTRIBUTE = QueryFactory.attribute(DeckSummary.class, Integer.class, "master_absolute", (DeckSummary deck) -> deck.getStats().getMaster());
    public static final Attribute<DeckSummary, Integer> ACTION_ABSOLUTE_ATTRIBUTE = QueryFactory.attribute(DeckSummary.class, Integer.class, "action_absolute", (DeckSummary deck) -> deck.getStats().getAction());
    public static final Attribute<DeckSummary, Integer> POLITICAL_ABSOLUTE_ATTRIBUTE = QueryFactory.attribute(DeckSummary.class, Integer.class, "political_absolute", (DeckSummary deck) -> deck.getStats().getPoliticalAction());
    public static final Attribute<DeckSummary, Integer> RETAINER_ABSOLUTE_ATTRIBUTE = QueryFactory.attribute(DeckSummary.class, Integer.class, "retainer_absolute", (DeckSummary deck) -> deck.getStats().getRetainer());
    public static final Attribute<DeckSummary, Integer> EQUIPMENT_ABSOLUTE_ATTRIBUTE = QueryFactory.attribute(DeckSummary.class, Integer.class, "equipment_absolute", (DeckSummary deck) -> deck.getStats().getEquipment());
    public static final Attribute<DeckSummary, Integer> ALLY_ABSOLUTE_ATTRIBUTE = QueryFactory.attribute(DeckSummary.class, Integer.class, "ally_absolute", (DeckSummary deck) -> deck.getStats().getAlly());
    public static final Attribute<DeckSummary, Integer> MODIFIER_ABSOLUTE_ATTRIBUTE = QueryFactory.attribute(DeckSummary.class, Integer.class, "modifier_absolute", (DeckSummary deck) -> deck.getStats().getActionModifier());
    public static final Attribute<DeckSummary, Integer> COMBAT_ABSOLUTE_ATTRIBUTE = QueryFactory.attribute(DeckSummary.class, Integer.class, "combat_absolute", (DeckSummary deck) -> deck.getStats().getCombat());
    public static final Attribute<DeckSummary, Integer> REACTION_ABSOLUTE_ATTRIBUTE = QueryFactory.attribute(DeckSummary.class, Integer.class, "reaction_absolute", (DeckSummary deck) -> deck.getStats().getReaction());
    public static final Attribute<DeckSummary, Integer> EVENT_ABSOLUTE_ATTRIBUTE = QueryFactory.attribute(DeckSummary.class, Integer.class, "event_absolute", (DeckSummary deck) -> deck.getStats().getEvent());
    public static final Attribute<DeckSummary, String> TAG_MULTI_ATTRIBUTE = new MultiValueNullableAttribute<DeckSummary, String>(true) {
        public Iterable<String> getNullableValues(DeckSummary deck, QueryOptions queryOptions) {
            return deck.getTags();
        }
    };
    public static final Attribute<DeckSummary, Integer> FAVORITE_MULTI_ATTRIBUTE = new MultiValueNullableAttribute<DeckSummary, Integer>(true) {
        public Iterable<Integer> getNullableValues(DeckSummary deck, QueryOptions queryOptions) {
            return deck.getFavoriteUsers();
        }
    };
    public static final Attribute<DeckSummary, String> LIMITED_FORMAT_ATTRIBUTE = QueryFactory.nullableAttribute(DeckSummary.class, String.class, "limited_format", (DeckSummary deck) -> StringUtils.lowerCase(deck.getLimitedFormat()));
    public static final Attribute<DeckSummary, String> PATH_ATTRIBUTE = QueryFactory.nullableAttribute(DeckSummary.class, String.class, "path", DeckSummary::getPath);
    public static final Attribute<DeckSummary, BigDecimal> PRICE_ATTRIBUTE = QueryFactory.nullableAttribute(DeckSummary.class, BigDecimal.class, "price", (DeckSummary deck) -> deck.getStats().getPrice());
    public static final Attribute<DeckSummary, Integer> ARCHETYPE_ATTRIBUTE = QueryFactory.nullableAttribute(DeckSummary.class, Integer.class, "archetype", DeckSummary::getDeckArchetypeId);
    public static final Attribute<DeckSummary, Boolean> DETAILED_ATTRIBUTE = QueryFactory.attribute(DeckSummary.class, Boolean.class, "detailed", DeckSummary::isDetailed);

    private String id;
    private DeckType type;
    private String name;
    private Long views;
    private Long viewsLastMonth;
    private Set<Integer> favoriteUsers;
    private Double rate;
    private Integer votes;
    private Long comments;
    private ReactionType reaction;
    private String tournament;
    private Integer players;
    private Integer rounds;
    private String place;
    private String country;
    private Integer year;
    private DeckUser user;
    private String author;
    private String set;
    private String limitedFormat;
    @Transient
    private JsonNode extra;
    private boolean published;
    private boolean collection;
    private boolean hasVideo;
    private boolean detailed;
    private Set<String> clanIcons;
    private Set<String> disciplineIcons;
    private Set<String> clans;
    private Set<Integer> groups;
    private Set<String> disciplines;
    private SummaryStats stats;
    private Set<String> tags;
    private Double l2Norm;
    private Integer deckArchetypeId;
    private String path;
    private String pathIcon;
    private LocalDateTime creationDate;
    private LocalDateTime modifyDate;

    public static DeckSummary from(Deck deck) {
        DeckSummary summary = new DeckSummary();
        summary.setId(deck.getId());
        summary.setType(deck.getType());
        summary.setName(deck.getName());
        summary.setViews(deck.getViews());
        summary.setViewsLastMonth(deck.getViewsLastMonth());
        summary.setFavoriteUsers(deck.getFavoriteUsers());
        summary.setRate(deck.getRate());
        summary.setVotes(deck.getVotes());
        summary.setComments(deck.getComments());
        summary.setReaction(deck.getReaction());
        summary.setTournament(deck.getTournament());
        summary.setPlayers(deck.getPlayers());
        summary.setRounds(deck.getRounds());
        summary.setPlace(deck.getPlace());
        summary.setCountry(deck.getCountry());
        summary.setYear(deck.getYear());
        summary.setUser(deck.getUser());
        summary.setAuthor(deck.getAuthor());
        summary.setSet(deck.getSet());
        summary.setLimitedFormat(deck.getLimitedFormat());
        JsonNode extra = deck.getExtra();
        summary.setExtra((extra != null && extra.has("advent")) ? extra.deepCopy() : null);
        summary.setPublished(deck.isPublished());
        summary.setCollection(deck.isCollection());
        summary.setHasVideo(deck.isHasVideo());
        summary.setDetailed(deck.isDetailed());
        summary.setClanIcons(deck.getClanIcons());
        summary.setDisciplineIcons(deck.getDisciplineIcons());
        summary.setClans(deck.getClans());
        summary.setGroups(deck.getGroups());
        summary.setDisciplines(deck.getDisciplines());
        summary.setStats(SummaryStats.from(deck.getStats()));
        summary.setTags(deck.getTags());
        summary.setL2Norm(deck.getL2Norm());
        summary.setDeckArchetypeId(deck.getDeckArchetypeId());
        summary.setPath(deck.getPath());
        summary.setPathIcon(deck.getPathIcon());
        summary.setCreationDate(deck.getCreationDate());
        summary.setModifyDate(deck.getModifyDate());
        return summary;
    }
}
