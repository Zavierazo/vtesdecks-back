package com.vtesdecks.cache.factory;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.indexable.Crypt;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.cache.indexable.DeckCard;
import com.vtesdecks.cache.indexable.Library;
import com.vtesdecks.cache.indexable.deck.ClanStat;
import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.cache.indexable.deck.DisciplineStat;
import com.vtesdecks.cache.indexable.deck.Stats;
import com.vtesdecks.cache.indexable.deck.card.Card;
import com.vtesdecks.db.CommentMapper;
import com.vtesdecks.db.DeckUserMapper;
import com.vtesdecks.db.DeckViewMapper;
import com.vtesdecks.db.UserMapper;
import com.vtesdecks.db.model.DbDeck;
import com.vtesdecks.db.model.DbDeckUser;
import com.vtesdecks.db.model.DbDeckView;
import com.vtesdecks.db.model.DbUser;
import com.vtesdecks.model.DeckTag;
import com.vtesdecks.model.Errata;
import com.vtesdecks.util.VtesUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.math.RoundingMode.HALF_UP;

@Component
public class DeckFactory {
    private static final List<String> DOMAIN_IGNORED = Lists.newArrayList("localhost", "beta.vtesdecks.com");
    private static final List<String> VIEWS_IGNORED =
            Lists.newArrayList("https://vtesdecks.com/",
                    "https://vtesdecks.com/decks",
                    "https://vtesdecks.com/decks?order=NEWEST",
                    "https://vtesdecks.com/decks?order=POPULAR",
                    "https://vtesdecks.com/decks?type=ALL",
                    "https://vtesdecks.com/decks?type=ALL&order=NEWEST",
                    "https://vtesdecks.com/decks?type=ALL&order=POPULAR",
                    "https://vtesdecks.com/decks?type=TOURNAMENT&order=NEWEST",
                    "https://vtesdecks.com/decks?type=TOURNAMENT&order=POPULAR",
                    "https://vtesdecks.com/decks?type=TOURNAMENT",
                    "https://vtesdecks.com/decks?type=COMMUNITY&order=NEWEST",
                    "https://vtesdecks.com/decks?type=COMMUNITY&order=POPULAR",
                    "https://vtesdecks.com/decks?type=COMMUNITY",
                    "/",
                    "/decks",
                    "/decks?order=NEWEST",
                    "/decks?order=POPULAR",
                    "/decks?type=ALL",
                    "/decks?type=ALL&order=NEWEST",
                    "/decks?type=ALL&order=POPULAR",
                    "/decks?type=TOURNAMENT&order=NEWEST",
                    "/decks?type=TOURNAMENT&order=POPULAR",
                    "/decks?type=TOURNAMENT",
                    "/decks?type=COMMUNITY&order=NEWEST",
                    "/decks?type=COMMUNITY&order=POPULAR",
                    "/decks?type=COMMUNITY");
    private static final String NEW_LINE_REGEX = "[^(\\.|>)]<br(\\/)?>";
    private static final String NEW_LINE_HTML = "<br/>";
    private static final String PARAGRAPH_START = "<p>";
    private static final String PARAGRAPH_END = "</p>";
    public static final String DECK_HASH_SEPARATOR = "#";
    @Autowired
    private DeckUserMapper deckUserMapper;
    @Autowired
    private DeckViewMapper deckViewMapper;
    @Autowired
    private CryptCache cryptCache;
    @Autowired
    private LibraryCache libraryCache;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private CommentMapper commentMapper;

    public Deck getDeck(DbDeck deck, List<DeckCard> deckCards) {
        Deck value = new Deck();
        value.setId(deck.getId());
        value.setType(deck.getType() != null ? DeckType.valueOf(deck.getType().name()) : null);
        value.setName(deck.getName());
        List<DbDeckView> views = deckViewMapper.selectByDeckId(deck.getId());
        value.setViewsLastMonth(getViewsLastMonth(deck.getId(), views));
        value.setViews(deck.getViews() + (views != null ? views.size() : 0));
        List<DbDeckUser> deckUsers = deckUserMapper.selectByDeckId(deck.getId());
        if (!CollectionUtils.isEmpty(deckUsers) && deckUsers.stream().anyMatch(deckUser -> isDeckRatingExcludingAuthor(deck, deckUser))) {
            value.setRate(Math.round(deckUsers.stream()
                    .filter(deckUser -> isDeckRatingExcludingAuthor(deck, deckUser))
                    .mapToDouble(DbDeckUser::getRate)
                    .average()
                    .getAsDouble() * 10) / 10.0);
            value.setVotes(deckUsers.stream()
                    .filter(deckUser -> isDeckRatingExcludingAuthor(deck, deckUser))
                    .mapToInt(e -> 1)
                    .sum());
        } else {
            value.setVotes(0);
        }
        value.setFavoriteUsers(deckUsers.stream().filter(DbDeckUser::isFavorite).map(DbDeckUser::getUser).collect(Collectors.toSet()));
        value.setComments(commentMapper.countByPageIdentifier("deck_" + deck.getId()));
        value.setTournament(deck.getTournament());
        value.setPlayers(deck.getPlayers());
        value.setYear(deck.getYear() != null ? deck.getYear() : deck.getCreationDate().getYear());
        value.setAuthor(deck.getAuthor());
        value.setUrl(deck.getUrl());
        value.setSource(deck.getSource());
        value.setDescription(getDescription(deck.getDescription()));
        value.setExtra(deck.getExtra());
        value.setPublished(deck.isPublished());
        if (deck.getUser() != null) {
            value.setUser(deck.getUser());
            DbUser user = userMapper.selectById(deck.getUser());
            if (user != null) {
                value.setAuthor(user.getDisplayName());
            }
        }
        if (deck.getExtra() != null && deck.getExtra().has("limitedFormat") && deck.getExtra().get("limitedFormat").has("name")) {
            if (deck.getExtra().get("limitedFormat").has("id")) {
                value.setLimitedFormat(deck.getExtra().get("limitedFormat").get("name").asText());
            } else {
                value.setLimitedFormat(deck.getExtra().get("limitedFormat").get("name").asText() + " (Custom)");
            }
        }
        List<Card> cards = new ArrayList<>();
        for (DeckCard deckCard : deckCards) {
            Card card = null;
            if (VtesUtils.isCrypt(deckCard.getId())) {
                card = new Card();
                card.setId(deckCard.getId());
                card.setNumber(deckCard.getNumber());
                value.getCrypt().add(card);
            } else if (VtesUtils.isLibrary(deckCard.getId())) {
                card = new Card();
                card.setId(deckCard.getId());
                card.setNumber(deckCard.getNumber());
                Library library = libraryCache.get(card.getId());
                if (!value.getLibraryByType().containsKey(library.getType())) {
                    value.getLibraryByType().put(library.getType(), new ArrayList<>());
                }
                value.getLibraryByType().get(library.getType()).add(card);
            }
            if (card != null) {
                cards.add(card);
            }
        }
        //Sort cards
        value.getCrypt().sort(Comparator.comparingInt(Card::getNumber)
                .thenComparingInt(card -> {
                    Crypt crypt = cryptCache.get(card.getId());
                    return crypt.getCapacity();
                }).reversed());
        for (List<Card> libraries : value.getLibraryByType().values()) {
            libraries.sort(Comparator.comparing(Card::getNumber).reversed());
        }
        //Other fields
        value.setClanIcons(cards
                .stream()
                .map(Card::getId)
                .filter(VtesUtils::isCrypt)
                .map(cryptCache::get)
                .map(Crypt::getClan)
                .map(VtesUtils::getClanIcon)
                .collect(Collectors.toSet()));
        value.setDisciplineIcons(cards
                .stream()
                .map(Card::getId)
                .filter(VtesUtils::isLibrary)
                .map(libraryCache::get)
                .map(Library::getDisciplineIcons)
                .flatMap(Set::stream)
                .collect(Collectors.toSet()));
        value.setClans(cards
                .stream()
                .map(Card::getId)
                .filter(VtesUtils::isCrypt)
                .map(cryptCache::get)
                .map(Crypt::getClan)
                .collect(Collectors.toSet()));
        value.setGroups(cards
                .stream()
                .map(Card::getId)
                .filter(VtesUtils::isCrypt)
                .map(cryptCache::get)
                .map(Crypt::getGroup)
                .collect(Collectors.toSet()));
        value.setDisciplines(cards
                .stream()
                .map(Card::getId)
                .filter(VtesUtils::isLibrary)
                .map(libraryCache::get)
                .map(Library::getDisciplines)
                .flatMap(Set::stream)
                .collect(Collectors.toSet()));
        value.setStats(getDeckStats(cards));
        value.setCreationDate(deck.getCreationDate());
        value.setModifyDate(deck.getModificationDate());
        LocalDate deckDate = value.getType() == DeckType.COMMUNITY && value.getModifyDate() != null ? value.getModifyDate().toLocalDate() : value.getCreationDate().toLocalDate();
        value.setErratas(cards
                .stream()
                .map(Card::getId)
                .map(id -> Errata.findErrata(id, deckDate))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList()));
        value.setTags(getDeckTags(value));
        return value;
    }

    private static boolean isDeckRatingExcludingAuthor(DbDeck deck, DbDeckUser deckUser) {
        return deckUser.getRate() != null && (deck.getUser() == null || !deck.getUser().equals(deckUser.getUser()));
    }

    private String getDescription(String description) {
        if (description == null) {
            return null;
        }
        List<String> descriptionParts = Splitter.on(NEW_LINE_HTML)
                .omitEmptyStrings()
                .trimResults()
                .splitToList(description.replaceAll(NEW_LINE_REGEX, ""));
        StringBuilder descriptionFix = new StringBuilder();
        for (String descriptionPart : descriptionParts) {
            if (StringUtils.isNotBlank(descriptionPart)) {
                descriptionFix.append(PARAGRAPH_START).append(descriptionPart).append(PARAGRAPH_END);
            }
        }
        return descriptionFix.length() > 0 ? descriptionFix.toString() : null;
    }

    private Long getViewsLastMonth(String deckId, List<DbDeckView> views) {
        return views != null
                ? views
                .stream()
                .filter(deckView -> deckView.getSource() == null ||
                        (!VIEWS_IGNORED.contains(deckView.getSource())
                                && DOMAIN_IGNORED.stream().noneMatch(deckView.getSource()::contains)
                                && !deckView.getSource().endsWith(deckId)))
                .count()
                : 0;
    }

    private Stats getDeckStats(List<Card> cards) {
        Stats deckStats = new Stats();
        List<Integer> groups = new ArrayList<>();
        for (Card card : cards) {
            if (VtesUtils.isCrypt(card.getId())) {
                deckStats.setCrypt(deckStats.getCrypt() + card.getNumber());
                Crypt crypt = cryptCache.get(card.getId());
                groups.add(crypt.getCapacity());
                fillCryptDisciplineStats(deckStats, crypt);
            } else if (VtesUtils.isLibrary(card.getId())) {
                deckStats.setLibrary(deckStats.getLibrary() + card.getNumber());
                Library library = libraryCache.get(card.getId());
                if (library.getBloodCost() != null) {
                    deckStats.setBloodCost(deckStats.getBloodCost() + (Math.max(0, library.getBloodCost()) * card.getNumber()));
                }
                if (library.getPoolCost() != null) {
                    deckStats.setPoolCost(deckStats.getPoolCost() + (Math.max(0, library.getPoolCost()) * card.getNumber()));
                }
                switch (library.getType()) {
                    case "Event":
                        deckStats.setEvent(deckStats.getEvent() + card.getNumber());
                        break;
                    case "Master":
                        deckStats.setMaster(deckStats.getMaster() + card.getNumber());
                        if (library.isTrifle()) {
                            deckStats.setMasterTrifle(deckStats.getMasterTrifle() + card.getNumber());
                        }
                        break;
                    case "Action":
                    case "Conviction":
                    case "Reflex":
                        deckStats.setAction(deckStats.getAction() + card.getNumber());
                        break;
                    case "Political Action":
                        deckStats.setPoliticalAction(deckStats.getPoliticalAction() + card.getNumber());
                        break;
                    case "Equipment":
                        deckStats.setEquipment(deckStats.getEquipment() + card.getNumber());
                        break;
                    case "Retainer":
                        deckStats.setRetainer(deckStats.getRetainer() + card.getNumber());
                        break;
                    case "Ally":
                        deckStats.setAlly(deckStats.getAlly() + card.getNumber());
                        break;
                    case "Action Modifier":
                        deckStats.setActionModifier(deckStats.getActionModifier() + card.getNumber());
                        break;
                    case "Combat":
                        deckStats.setCombat(deckStats.getCombat() + card.getNumber());
                        break;
                    case "Reaction":
                        deckStats.setReaction(deckStats.getReaction() + card.getNumber());
                        break;
                    case "Reaction/Combat":
                        deckStats.setReaction(deckStats.getReaction() + card.getNumber());
                        deckStats.setCombat(deckStats.getCombat() + card.getNumber());
                        break;
                    case "Action Modifier/Reaction":
                    case "Reaction/Action Modifier":
                        deckStats.setActionModifier(deckStats.getActionModifier() + card.getNumber());
                        deckStats.setReaction(deckStats.getReaction() + card.getNumber());
                        break;
                    case "Action Modifier/Combat":
                    case "Combat/Action Modifier":
                        deckStats.setActionModifier(deckStats.getActionModifier() + card.getNumber());
                        deckStats.setCombat(deckStats.getCombat() + card.getNumber());
                        break;
                    case "Combat/Reaction":
                        deckStats.setCombat(deckStats.getCombat() + card.getNumber());
                        deckStats.setReaction(deckStats.getReaction() + card.getNumber());
                        break;
                    case "Action/Reaction":
                        deckStats.setAction(deckStats.getAction() + card.getNumber());
                        deckStats.setReaction(deckStats.getReaction() + card.getNumber());
                        break;
                    case "Action/Combat":
                        deckStats.setAction(deckStats.getAction() + card.getNumber());
                        deckStats.setCombat(deckStats.getCombat() + card.getNumber());
                        break;
                    case "Power":
                        if (library.getText().contains("[ACTION]")) {
                            deckStats.setAction(deckStats.getAction() + card.getNumber());
                        }
                        if (library.getText().contains("[ACTION MODIFIER]")) {
                            deckStats.setActionModifier(deckStats.getActionModifier() + card.getNumber());
                        }
                        if (library.getText().contains("[COMBAT]")) {
                            deckStats.setCombat(deckStats.getCombat() + card.getNumber());
                        }
                        if (library.getText().contains("[REACTION]")) {
                            deckStats.setReaction(deckStats.getReaction() + card.getNumber());
                        }
                        break;
                }
                fillLibraryDisciplineStats(deckStats, library);
                fillLibraryClanStats(deckStats, library);
            }
        }
        deckStats.setAvgCrypt(new BigDecimal(groups.stream().mapToInt(Integer::valueOf).average().orElse(0)).setScale(2, RoundingMode.HALF_UP));
        Collections.sort(groups);
        deckStats.setMinCrypt(groups.stream().mapToInt(Integer::valueOf).limit(4).sum());
        Collections.reverse(groups);
        deckStats.setMaxCrypt(groups.stream().mapToInt(Integer::valueOf).limit(4).sum());
        Collections.sort(deckStats.getCryptDisciplines(), Comparator.comparingInt(DisciplineStat::getSuperior).reversed());
        Collections.sort(deckStats.getLibraryDisciplines(), Comparator.comparingInt(DisciplineStat::getInferior).reversed());
        Collections.sort(deckStats.getLibraryClans(), Comparator.comparingInt(ClanStat::getNumber).reversed());
        return deckStats;
    }

    private void fillCryptDisciplineStats(Stats stats, Crypt crypt) {
        List<DisciplineStat> disciplineStats = stats.getCryptDisciplines();
        for (String discipline : crypt.getSuperiorDisciplines()) {
            DisciplineStat disciplineStat = disciplineStats.stream()
                    .filter(d -> d.getDisciplines().contains(discipline))
                    .findAny().orElse(null);
            if (disciplineStat == null) {
                disciplineStat = new DisciplineStat();
                disciplineStat.setDisciplines(Sets.newHashSet(discipline));
                disciplineStat.setInferior(0);
                disciplineStat.setSuperior(1);
                disciplineStats.add(disciplineStat);
            } else {
                disciplineStat.setSuperior(disciplineStat.getSuperior() + 1);
            }
        }
        for (String discipline : crypt.getDisciplines()) {
            if (crypt.getSuperiorDisciplines().contains(discipline)) {
                continue;
            }
            DisciplineStat disciplineStat = disciplineStats.stream()
                    .filter(d -> d.getDisciplines().contains(discipline))
                    .findAny().orElse(null);
            if (disciplineStat == null) {
                disciplineStat = new DisciplineStat();
                disciplineStat.setDisciplines(Sets.newHashSet(discipline));
                disciplineStat.setInferior(1);
                disciplineStat.setSuperior(0);
                disciplineStats.add(disciplineStat);
            } else {
                disciplineStat.setInferior(disciplineStat.getInferior() + 1);
            }
        }
    }

    private void fillLibraryDisciplineStats(Stats stats, Library library) {
        if (library.getType().equals("Master") || library.getType().equals("Event")) {
            return;
        }
        List<DisciplineStat> disciplineStats = stats.getLibraryDisciplines();
        DisciplineStat disciplineStat = disciplineStats.stream()
                .filter(d -> d.getDisciplines().size() == library.getDisciplines().size() && d.getDisciplines().containsAll(library.getDisciplines()))
                .findAny().orElse(null);
        if (disciplineStat == null) {
            disciplineStat = new DisciplineStat();
            disciplineStat.setDisciplines(library.getDisciplines());
            disciplineStat.setInferior(1);
            disciplineStat.setSuperior(0);
            disciplineStats.add(disciplineStat);
        } else {
            disciplineStat.setInferior(disciplineStat.getInferior() + 1);
        }

    }

    private void fillLibraryClanStats(Stats stats, Library library) {
        if (CollectionUtils.isEmpty(library.getClans())) {
            return;
        }
        List<ClanStat> clanStats = stats.getLibraryClans();
        ClanStat clanStat = clanStats.stream()
                .filter(d -> d.getClans().size() == library.getClans().size() && d.getClans().containsAll(library.getClans()))
                .findAny().orElse(null);
        if (clanStat == null) {
            clanStat = new ClanStat();
            clanStat.setClans(library.getClans());
            clanStat.setNumber(1);
            clanStats.add(clanStat);
        } else {
            clanStat.setNumber(clanStat.getNumber() + 1);
        }

    }

    private Set<String> getDeckTags(Deck deck) {
        Map<DeckTag, BigDecimal> deckTagScore = new EnumMap<>(DeckTag.class);
        if (!CollectionUtils.isEmpty(deck.getCrypt())) {
            BigDecimal cryptFactor = BigDecimal.valueOf(12.0).divide(BigDecimal.valueOf(deck.getStats().getCrypt()), HALF_UP).multiply(BigDecimal.valueOf(1.5));
            for (Card card : deck.getCrypt()) {
                Crypt crypt = cryptCache.get(card.getId());
                if (crypt != null) {
                    for (DeckTag deckTag : DeckTag.values()) {
                        if (Boolean.TRUE.equals(deckTag.getCryptTest().test(crypt))) {
                            BigDecimal score = deckTagScore.getOrDefault(deckTag, BigDecimal.ZERO);
                            BigDecimal newScore = score.add(BigDecimal.valueOf(card.getNumber()).multiply(cryptFactor));
                            deckTagScore.put(deckTag, newScore);
                        }
                    }
                }
            }
        }
        if (deck.getLibraryByType() != null && !deck.getLibraryByType().isEmpty()) {
            BigDecimal libraryFactor = BigDecimal.valueOf(90.0).divide(BigDecimal.valueOf(deck.getStats().getLibrary()), HALF_UP);
            for (List<Card> cards : deck.getLibraryByType().values()) {
                for (Card card : cards) {
                    Library library = libraryCache.get(card.getId());
                    if (library != null) {
                        for (DeckTag deckTag : DeckTag.values()) {
                            if (Boolean.TRUE.equals(deckTag.getLibraryTest().test(library))) {
                                BigDecimal score = deckTagScore.getOrDefault(deckTag, BigDecimal.ZERO);
                                BigDecimal newScore = score.add(BigDecimal.valueOf(card.getNumber()).multiply(libraryFactor));
                                deckTagScore.put(deckTag, newScore);
                            }
                        }
                    }
                }
            }
            if (deck.getStats().getMaster() > 20) {
                BigDecimal score = deckTagScore.getOrDefault(DeckTag.MMPA, BigDecimal.ZERO);
                BigDecimal newScore = score.add(BigDecimal.valueOf(deck.getStats().getMaster() - 20L).multiply(libraryFactor));
                deckTagScore.put(DeckTag.MMPA, newScore);
            }
        }
        return deckTagScore.entrySet().stream()
                .filter(entry -> entry.getValue().compareTo(BigDecimal.valueOf(entry.getKey().getThreshold())) >= 0)
                .map(Map.Entry::getKey)
                .filter(deckTag -> deckTag.getDeckTest().test(deck))
                .map(DeckTag::getTag)
                .collect(Collectors.toSet());
    }

}
