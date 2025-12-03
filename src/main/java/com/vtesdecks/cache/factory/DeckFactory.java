package com.vtesdecks.cache.factory;

import com.fasterxml.jackson.databind.JsonNode;
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
import com.vtesdecks.jpa.entity.DeckEntity;
import com.vtesdecks.jpa.entity.DeckUserEntity;
import com.vtesdecks.jpa.entity.DeckViewEntity;
import com.vtesdecks.jpa.repositories.CommentRepository;
import com.vtesdecks.jpa.repositories.DeckUserRepository;
import com.vtesdecks.jpa.repositories.DeckViewRepository;
import com.vtesdecks.jpa.repositories.UserRepository;
import com.vtesdecks.model.DeckTag;
import com.vtesdecks.model.Errata;
import com.vtesdecks.model.limitedformat.LimitedFormatPayload;
import com.vtesdecks.util.CosineSimilarityUtils;
import com.vtesdecks.util.VtesUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.vtesdecks.util.Constants.DEFAULT_CURRENCY;
import static java.math.RoundingMode.HALF_UP;
import static org.apache.commons.lang3.StringUtils.isEmpty;

@Slf4j
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
    @Autowired
    private DeckUserRepository deckUserRepository;
    @Autowired
    private DeckViewRepository deckViewRepository;
    @Autowired
    private CryptCache cryptCache;
    @Autowired
    private LibraryCache libraryCache;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CommentRepository commentRepository;

    public Deck getDeck(DeckEntity deck, List<DeckCard> deckCards, List<LimitedFormatPayload> limitedFormats) {
        Deck value = new Deck();
        value.setId(deck.getId());
        value.setType(deck.getType() != null ? DeckType.valueOf(deck.getType().name()) : null);
        value.setName(deck.getName());
        List<DeckViewEntity> views = deckViewRepository.findByIdDeckId(deck.getId());
        value.setViewsLastMonth(getViewsLastMonth(deck.getId(), views));
        value.setViews(deck.getViews() + (views != null ? views.size() : 0));
        List<DeckUserEntity> deckUsers = deckUserRepository.findByIdDeckId(deck.getId());
        if (!CollectionUtils.isEmpty(deckUsers) && deckUsers.stream().anyMatch(deckUser -> isDeckRatingExcludingAuthor(deck, deckUser))) {
            value.setRate(Math.round(deckUsers.stream()
                    .filter(deckUser -> isDeckRatingExcludingAuthor(deck, deckUser))
                    .mapToDouble(DeckUserEntity::getRate)
                    .average()
                    .getAsDouble() * 10) / 10.0);
            value.setVotes(deckUsers.stream()
                    .filter(deckUser -> isDeckRatingExcludingAuthor(deck, deckUser))
                    .mapToInt(e -> 1)
                    .sum());
        } else {
            value.setVotes(0);
        }
        value.setFavoriteUsers(deckUsers.stream().filter(du -> du.getFavorite() != null && du.getFavorite()).map(du -> du.getId().getUser()).collect(Collectors.toSet()));
        value.setComments(commentRepository.countByPageIdentifierAndDeletedFalse("deck_" + deck.getId()));
        value.setTournament(deck.getTournament());
        value.setPlayers(deck.getPlayers());
        value.setYear(deck.getYear() != null ? deck.getYear() : deck.getCreationDate().getYear());
        value.setAuthor(deck.getAuthor());
        value.setUrl(deck.getUrl());
        value.setSource(deck.getSource());
        value.setDescription(deck.getDescription());
        value.setSet(deck.getSet());
        value.setExtra(deck.getExtra());
        value.setPublished(deck.getPublished());
        value.setCollection(deck.getCollection());
        if (deck.getUser() != null) {
            value.setUser(deck.getUser());
            userRepository.findById(deck.getUser()).ifPresent(user -> value.setAuthor(user.getDisplayName()));
        }
        if (deck.getExtra() != null && deck.getExtra().has("limitedFormat") && deck.getExtra().get("limitedFormat").has("name")) {
            if (deck.getExtra().get("limitedFormat").has("id")) {
                value.setLimitedFormat(deck.getExtra().get("limitedFormat").get("name").asText());
            } else {
                value.setLimitedFormat(deck.getExtra().get("limitedFormat").get("name").asText() + " (Custom)");
            }
        }
        if (deck.getExtra() != null && deck.getExtra().has("advent")) {
            JsonNode advent = deck.getExtra().get("advent");
            value.setLimitedFormat("Advent " + advent.get("year") + " - Day " + advent.get("day"));
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
        value.setPath(cards
                .stream()
                .map(Card::getId)
                .filter(VtesUtils::isLibrary)
                .map(libraryCache::get)
                .map(Library::getPath)
                .filter(Objects::nonNull)
                .findAny().orElse(null));
        value.setPathIcon(cards
                .stream()
                .map(Card::getId)
                .filter(VtesUtils::isLibrary)
                .map(libraryCache::get)
                .map(Library::getPathIcon)
                .filter(Objects::nonNull)
                .findAny().orElse(null));
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
                .toList());
        value.setTags(getDeckTags(value, limitedFormats));
        value.setL2Norm(CosineSimilarityUtils.computeL2Norm(CosineSimilarityUtils.getVector(value)));
        return value;
    }

    private static boolean isDeckRatingExcludingAuthor(DeckEntity deck, DeckUserEntity deckUser) {
        return deckUser.getRate() != null && (deck.getUser() == null || !deck.getUser().equals(deckUser.getId().getUser()));
    }

    private Long getViewsLastMonth(String deckId, List<DeckViewEntity> views) {
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
        BigDecimal price = BigDecimal.ZERO;
        boolean fullPrice = true;
        for (Card card : cards) {
            if (VtesUtils.isCrypt(card.getId())) {
                deckStats.setCrypt(deckStats.getCrypt() + card.getNumber());
                Crypt crypt = cryptCache.get(card.getId());
                groups.add(crypt.getCapacity());
                fillCryptDisciplineStats(deckStats, crypt);
                if (fullPrice && crypt.getMinPrice() != null) {
                    price = price.add(crypt.getMinPrice().multiply(BigDecimal.valueOf(card.getNumber())));
                } else {
                    fullPrice = false;
                }
            } else if (VtesUtils.isLibrary(card.getId())) {
                deckStats.setLibrary(deckStats.getLibrary() + card.getNumber());
                Library library = libraryCache.get(card.getId());
                if (library.getBloodCost() != null) {
                    deckStats.setBloodCost(deckStats.getBloodCost() + (Math.max(0, library.getBloodCost()) * card.getNumber()));
                }
                if (library.getPoolCost() != null) {
                    deckStats.setPoolCost(deckStats.getPoolCost() + (Math.max(0, library.getPoolCost()) * card.getNumber()));
                }
                if (fullPrice && library.getMinPrice() != null) {
                    price = price.add(library.getMinPrice().multiply(BigDecimal.valueOf(card.getNumber())));
                } else {
                    fullPrice = false;
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
        deckStats.getCryptDisciplines().sort(Comparator.comparingInt(DisciplineStat::getSuperior).reversed());
        deckStats.getLibraryDisciplines().sort(Comparator.comparingInt(DisciplineStat::getInferior).reversed());
        deckStats.getLibraryClans().sort(Comparator.comparingInt(ClanStat::getNumber).reversed());
        // Set price and currency only if all cards have price
        if (fullPrice) {
            deckStats.setPrice(price);
            deckStats.setCurrency(DEFAULT_CURRENCY);
        }
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

    private Set<String> getDeckTags(Deck deck, List<LimitedFormatPayload> limitedFormats) {
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
        Set<String> tags = deckTagScore.entrySet().stream()
                .filter(entry -> entry.getValue().compareTo(BigDecimal.valueOf(entry.getKey().getThreshold())) >= 0)
                .map(Map.Entry::getKey)
                .filter(deckTag -> deckTag.getDeckTest().test(deck))
                .map(DeckTag::getTag)
                .collect(Collectors.toSet());
        for (LimitedFormatPayload limitedFormat : limitedFormats) {
            if (limitedFormat.getTag() != null && isValidForLimitedFormat(deck, limitedFormat)) {
                tags.add(limitedFormat.getTag());
            }
        }
        return tags;
    }

    private boolean isValidForLimitedFormat(Deck deck, LimitedFormatPayload limitedFormat) {
        boolean isValid = true;
        int cryptSize = deck.getStats().getCrypt();
        int minCrypt = limitedFormat.getMinCrypt() != null ? limitedFormat.getMinCrypt() : 12;
        Integer maxCrypt = limitedFormat.getMaxCrypt();

        if (cryptSize < minCrypt) {
            isValid = false;
        }
        if (maxCrypt != null && cryptSize > maxCrypt) {
            isValid = false;
        }

        Set<Integer> groups = new HashSet<>();
        for (Card crypt : deck.getCrypt()) {
            Crypt cryptInfo = cryptCache.get(crypt.getId());
            if (!isEmpty(cryptInfo.getBanned())) {
                isValid = false;
            } else {
                boolean allowed = limitedFormat.getAllowed().getCrypt().containsKey(String.valueOf(crypt.getId()));
                boolean banned = limitedFormat.getBanned().getCrypt().containsKey(String.valueOf(crypt.getId()));
                boolean inSet = limitedFormat.getSets().keySet().stream().anyMatch(set ->
                        cryptInfo.getSets().stream().anyMatch(cryptSet -> cryptSet.split(":")[0].equals(set))
                );
                if (!allowed && (banned || !inSet)) {
                    isValid = false;
                }
            }
            if (cryptInfo.getGroup() > 0) {
                groups.add(cryptInfo.getGroup());
            }
        }
        if (groups.size() > 2) {
            isValid = false;
        } else if (groups.size() > 1) {
            List<Integer> sortedGroups = new ArrayList<>(groups);
            Collections.sort(sortedGroups);
            if (sortedGroups.get(1) - sortedGroups.get(0) > 1) {
                isValid = false;
            }
        }

        int librarySize = deck.getStats().getLibrary();
        int minLibrary = limitedFormat.getMinLibrary() != null ? limitedFormat.getMinLibrary() : 60;
        int maxLibrary = limitedFormat.getMaxLibrary() != null ? limitedFormat.getMaxLibrary() : 90;

        if (librarySize < minLibrary) {
            isValid = false;
        }
        if (librarySize > maxLibrary) {
            isValid = false;
        }
        for (List<Card> libraries : deck.getLibraryByType().values()) {
            for (Card library : libraries) {
                Library libraryInfo = libraryCache.get(library.getId());
                if (!isEmpty(libraryInfo.getBanned())) {
                    isValid = false;
                } else {
                    boolean allowed = limitedFormat.getAllowed().getLibrary().containsKey(String.valueOf(library.getId()));
                    boolean banned = limitedFormat.getBanned().getLibrary().containsKey(String.valueOf(library.getId()));
                    boolean inSet = limitedFormat.getSets().keySet().stream().anyMatch(set ->
                            libraryInfo.getSets().stream().anyMatch(librarySet -> librarySet.split(":")[0].equals(set))
                    );
                    if (!allowed && (banned || !inSet)) {
                        isValid = false;
                    }
                }
            }
        }

        return isValid;
    }


}
