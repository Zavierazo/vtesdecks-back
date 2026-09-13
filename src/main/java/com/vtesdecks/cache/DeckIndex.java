package com.vtesdecks.cache;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Striped;
import com.googlecode.cqengine.ConcurrentIndexedCollection;
import com.googlecode.cqengine.IndexedCollection;
import com.googlecode.cqengine.attribute.Attribute;
import com.googlecode.cqengine.index.hash.HashIndex;
import com.googlecode.cqengine.index.navigable.NavigableIndex;
import com.googlecode.cqengine.index.unique.UniqueIndex;
import com.googlecode.cqengine.query.Query;
import com.googlecode.cqengine.query.QueryFactory;
import com.googlecode.cqengine.query.option.DeduplicationOption;
import com.googlecode.cqengine.query.option.DeduplicationStrategy;
import com.googlecode.cqengine.query.option.QueryOptions;
import com.googlecode.cqengine.query.option.Thresholds;
import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.cache.factory.DeckFactory;
import com.vtesdecks.cache.indexable.Crypt;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.cache.indexable.DeckCard;
import com.vtesdecks.cache.indexable.DeckSummary;
import com.vtesdecks.cache.indexable.Library;
import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.cache.redis.repositories.DeckRedisRepository;
import com.vtesdecks.jpa.entity.DeckEntity;
import com.vtesdecks.jpa.entity.LimitedFormatEntity;
import com.vtesdecks.jpa.entity.UserEntity;
import com.vtesdecks.jpa.repositories.DeckRepository;
import com.vtesdecks.jpa.repositories.LimitedFormatRepository;
import com.vtesdecks.jpa.repositories.UserRepository;
import com.vtesdecks.model.DeckQuery;
import com.vtesdecks.model.limitedformat.LimitedFormatPayload;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

import static com.googlecode.cqengine.query.QueryFactory.all;
import static com.googlecode.cqengine.query.QueryFactory.applyThresholds;
import static com.googlecode.cqengine.query.QueryFactory.ascending;
import static com.googlecode.cqengine.query.QueryFactory.contains;
import static com.googlecode.cqengine.query.QueryFactory.deduplicate;
import static com.googlecode.cqengine.query.QueryFactory.descending;
import static com.googlecode.cqengine.query.QueryFactory.equal;
import static com.googlecode.cqengine.query.QueryFactory.existsIn;
import static com.googlecode.cqengine.query.QueryFactory.greaterThanOrEqualTo;
import static com.googlecode.cqengine.query.QueryFactory.has;
import static com.googlecode.cqengine.query.QueryFactory.in;
import static com.googlecode.cqengine.query.QueryFactory.lessThanOrEqualTo;
import static com.googlecode.cqengine.query.QueryFactory.not;
import static com.googlecode.cqengine.query.QueryFactory.orderBy;
import static com.googlecode.cqengine.query.QueryFactory.queryOptions;
import static com.googlecode.cqengine.query.QueryFactory.threshold;
import static com.googlecode.cqengine.query.option.EngineThresholds.INDEX_ORDERING_SELECTIVITY;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DeckIndex {
    private static final List<DeckType> ALL_DECK_TYPES = Lists.newArrayList(DeckType.TOURNAMENT, DeckType.COMMUNITY);
    private static final int CRYPT_MAIN_MIN_NUMBER = 4;
    @Autowired
    private DeckRepository deckRepository;
    @Autowired
    private DeckFactory deckFactory;
    @Autowired
    private CryptCache cryptCache;
    @Autowired
    private LibraryCache libraryCache;
    @Autowired
    private DeckCardIndex deckCardIndex;
    @Autowired
    private LimitedFormatRepository limitedFormatRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DeckRedisRepository deckRedisRepository;
    @Value("${deck.cache.ttl:15m}")
    private Duration cacheTtl = Duration.ofMinutes(15);
    private final Striped<Lock> locks = Striped.lock(256);
    private IndexedCollection<DeckSummary> decks = new ConcurrentIndexedCollection<>();


    @PostConstruct
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void setUp() {
        //Id is always unique and is the Primary Key
        decks.addIndex(UniqueIndex.onAttribute(DeckSummary.ID_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(DeckSummary.PUBLISHED_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(DeckSummary.TYPE_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(DeckSummary.USER_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(DeckSummary.AUTHOR_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(DeckSummary.ROUNDS_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(DeckSummary.CLAN_MULTI_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(DeckSummary.DISCIPLINE_MULTI_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(DeckSummary.GROUP_MULTI_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(DeckSummary.CLAN_NUMBER_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(DeckSummary.DISCIPLINE_NUMBER_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(DeckSummary.TAG_MULTI_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(DeckSummary.FAVORITE_MULTI_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(DeckSummary.PATH_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(DeckSummary.PRICE_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(DeckSummary.ARCHETYPE_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(DeckSummary.DETAILED_ATTRIBUTE));
        // Used for most common sort filters
        decks.addIndex(NavigableIndex.onAttribute((Attribute) DeckSummary.CREATION_DATE_ATTRIBUTE));
        decks.addIndex(NavigableIndex.onAttribute(DeckSummary.VIEWS_LAST_MONTH_ATTRIBUTE));
    }


    @Scheduled(cron = "${jobs.cache.deck.refresh:0 0 * * * *}")
    public void refreshIndex() {
        StopWatch stopWatch = new StopWatch();
        try (ExecutorService executor = Executors.newFixedThreadPool(5)) {
            stopWatch.start();
            Set<String> currentKeys = decks.stream().map(DeckSummary::getId).collect(Collectors.toSet());

            List<LimitedFormatPayload> limitedFormats = getLimitedFormats();
            for (DeckEntity deck : deckRepository.findAll()) {
                if (Boolean.FALSE.equals(deck.getDeleted())) {
                    executor.execute(() -> refreshIndex(deck.getId(), limitedFormats));
                    currentKeys.remove(deck.getId());
                }
            }
            if (!currentKeys.isEmpty()) {
                log.warn("Deleting from index decks {}", currentKeys);
                for (String deleteKeys : currentKeys) {
                    refreshIndex(deleteKeys, limitedFormats);
                }
            }
        } finally {
            stopWatch.stop();
            log.info("Index finished in {} ms. Collection size is {}", stopWatch.lastTaskInfo().getTimeMillis(), decks.size());
        }
    }

    public void refreshIndex(String deckId) {
        refreshIndex(deckId, getLimitedFormats());
    }

    private void refreshIndex(String deckId, List<LimitedFormatPayload> limitedFormats) {
        var lock = locks.get(deckId);
        lock.lock();
        try {
            rebuild(deckId, limitedFormats, false);
        } catch (Exception e) {
            log.error("Error when refreshing deck {}", deckId, e);
        } finally {
            lock.unlock();
        }
    }

    /** Only detail/export requests enter this path. Full decks live in Redis, never in this index. */
    public Deck getFull(String deckId) {
        var lock = locks.get(deckId);
        lock.lock();
        try {
            DeckSummary summary = get(deckId);
            if (summary == null) {
                return null;
            }
            try {
                Deck cached = deckRedisRepository.findById(deckId).orElse(null);
                if (cached != null && deckId.equals(cached.getId())) {
                    return cached;
                }
            } catch (Exception e) {
                log.warn("Deck payload cache read failed for {}: {}", deckId, e.toString());
            }
            return rebuild(deckId, getLimitedFormats(), true);
        } finally {
            lock.unlock();
        }
    }

    private Deck rebuild(String deckId, List<LimitedFormatPayload> limitedFormats, boolean cachePayload) {
        Optional<DeckEntity> entity = deckRepository.findById(deckId);
        if (entity.isEmpty() || !Boolean.FALSE.equals(entity.get().getDeleted())) {
            deleteDeck(deckId);
            return null;
        }
        DeckCardIndex.RefreshResult cards = deckCardIndex.refreshIndex(deckId);
        Deck full = deckFactory.getDeck(entity.get(), cards.cards(), limitedFormats, cards.modificationDate());
        DeckSummary summary = DeckSummary.from(full);
        syncDeck(summary);
        try {
            // @TimeToLive reads the remaining Redis TTL, so refreshes do not renew it.
            long ttl = cachePayload ? Math.max(1L, cacheTtl.toSeconds())
                    : deckRedisRepository.findById(deckId).map(Deck::getCacheTtl).orElse(0L);
            if (ttl > 0) {
                full.setCacheTtl(ttl);
                deckRedisRepository.save(full);
            }
        } catch (Exception e) {
            log.warn("Deck payload cache write failed for {}: {}", deckId, e.toString());
            if (!cachePayload) {
                invalidate(deckId);
            }
        }
        return full;
    }

    private void syncDeck(DeckSummary summary) {
        DeckSummary oldDeck = get(summary.getId());
        if (oldDeck != null && !oldDeck.equals(summary)) {
            decks.update(List.of(oldDeck), List.of(summary));
        } else if (oldDeck == null) {
            decks.add(summary);
        }
    }

    private void deleteDeck(String deckId) {
        DeckSummary deck = get(deckId);
        if (deck != null) {
            decks.remove(deck);
        }
        deckCardIndex.removeDeck(deckId);
        invalidate(deckId);
    }

    private void invalidate(String deckId) {
        try {
            deckRedisRepository.deleteById(deckId);
        } catch (Exception e) {
            log.warn("Deck payload cache invalidation failed for {}: {}", deckId, e.toString());
        }
    }

    private List<LimitedFormatPayload> getLimitedFormats() {
        List<LimitedFormatEntity> limitedFormatEntities = limitedFormatRepository.findAll();
        return limitedFormatEntities.stream().map(LimitedFormatEntity::getFormat).toList();
    }


    public DeckSummary get(String id) {
        Query<DeckSummary> findByKeyQuery = equal(DeckSummary.ID_ATTRIBUTE, id);
        try (ResultSet<DeckSummary> result = decks.retrieve(findByKeyQuery)) {
            return (!result.isEmpty()) ? result.uniqueResult() : null;
        }
    }

    public ResultSet<DeckSummary> selectAll(DeckQuery deckQuery) {
        DeduplicationOption deduplication = deduplicate(DeduplicationStrategy.MATERIALIZE);
        Thresholds threshold = applyThresholds(threshold(INDEX_ORDERING_SELECTIVITY, 1.0));
        QueryOptions queryOptions;
        Query<DeckSummary> query = null;
        switch (deckQuery.getOrder()) {
            case NAME:
                queryOptions = queryOptions(orderBy(ascending(DeckSummary.NAME_ATTRIBUTE),
                        descending(DeckSummary.CREATION_DATE_ATTRIBUTE)), threshold, deduplication);
                break;
            case VOTES:
                queryOptions = queryOptions(orderBy(descending(DeckSummary.VOTES_ATTRIBUTE),
                        descending(DeckSummary.RATE_ATTRIBUTE),
                        descending(DeckSummary.CREATION_DATE_ATTRIBUTE)), threshold, deduplication);
                break;
            case RATE:
                queryOptions = queryOptions(orderBy(descending(DeckSummary.RATE_ATTRIBUTE),
                        descending(DeckSummary.VOTES_ATTRIBUTE),
                        descending(DeckSummary.CREATION_DATE_ATTRIBUTE)), threshold, deduplication);
                break;
            case VIEWS:
                queryOptions = queryOptions(orderBy(descending(DeckSummary.VIEWS_ATTRIBUTE),
                        descending(DeckSummary.CREATION_DATE_ATTRIBUTE)), threshold, deduplication);
                break;
            case COMMENTS:
                queryOptions = queryOptions(orderBy(descending(DeckSummary.COMMENTS_ATTRIBUTE),
                        descending(DeckSummary.CREATION_DATE_ATTRIBUTE)), threshold, deduplication);
                break;
            case MODIFIED:
                queryOptions = queryOptions(orderBy(descending(DeckSummary.MODIFY_DATE_ATTRIBUTE)), threshold, deduplication);
                break;
            case OLDEST:
                queryOptions = queryOptions(orderBy(ascending(DeckSummary.CREATION_DATE_ATTRIBUTE)), threshold, deduplication);
                break;
            case POPULAR:
                queryOptions = queryOptions(orderBy(descending(DeckSummary.VIEWS_LAST_MONTH_ATTRIBUTE),
                                descending(DeckSummary.VIEWS_ATTRIBUTE),
                                descending(DeckSummary.RATE_ATTRIBUTE),
                                descending(DeckSummary.CREATION_DATE_ATTRIBUTE)),
                        threshold,
                        deduplication);
                break;
            case PLAYERS:
                queryOptions = queryOptions(orderBy(descending(DeckSummary.PLAYERS_ATTRIBUTE),
                        descending(DeckSummary.CREATION_DATE_ATTRIBUTE)), threshold, deduplication);
                break;
            case CHEAPEST:
                query = and(query, has(DeckSummary.PRICE_ATTRIBUTE));
                queryOptions = queryOptions(orderBy(ascending(DeckSummary.PRICE_ATTRIBUTE), descending(DeckSummary.CREATION_DATE_ATTRIBUTE)), threshold, deduplication);
                break;
            case EXPENSIVE:
                query = and(query, has(DeckSummary.PRICE_ATTRIBUTE));
                queryOptions = queryOptions(orderBy(descending(DeckSummary.PRICE_ATTRIBUTE), descending(DeckSummary.CREATION_DATE_ATTRIBUTE)), threshold, deduplication);
                break;
            case NEWEST:
            default:
                queryOptions = queryOptions(orderBy(descending(DeckSummary.CREATION_DATE_ATTRIBUTE)), threshold, deduplication);
        }
        Query<DeckSummary> published = equal(DeckSummary.PUBLISHED_ATTRIBUTE, true);
        if (deckQuery.isAllDecks()) {
            // Skip published and type filters — returns every non-deleted deck in the index
            query = and(query, all(DeckSummary.class));
        } else if (deckQuery.getUserId() != null) {
            query = and(query, or(
                    published, // DeckSummary is public
                    or(
                            equal(DeckSummary.USER_ATTRIBUTE, deckQuery.getUserId()), // DeckSummary is owned by requesting user
                            in(DeckSummary.FAVORITE_MULTI_ATTRIBUTE, deckQuery.getUserId()) // DeckSummary is bookmarked by requesting user
                    )
            ));
        } else {
            query = and(query, published);
        }
        if (deckQuery.getUsername() != null) {
            UserEntity filterUser = userRepository.findByUsername(deckQuery.getUsername());
            if (filterUser != null) {
                query = and(query, equal(DeckSummary.USER_ATTRIBUTE, filterUser.getId()));
            } else {
                query = and(query, equal(DeckSummary.USER_ATTRIBUTE, -1));
            }
        }
        if (deckQuery.getCards() != null && !deckQuery.getCards().isEmpty()) {
            for (Map.Entry<Integer, Integer> card : deckQuery.getCards().entrySet()) {
                Integer cardId = card.getKey();
                Integer cardNumber = card.getValue();
                query = and(query, existsIn(
                        deckCardIndex.getRepository(),
                        DeckSummary.ID_ATTRIBUTE,
                        DeckCard.DECK_ID_ATTRIBUTE,
                        QueryFactory.and(in(DeckCard.CARD_ID_ATTRIBUTE, cardId),
                                greaterThanOrEqualTo(DeckCard.NUMBER_ATTRIBUTE, cardNumber))));
            }
        }
        if (CollectionUtils.isNotEmpty(deckQuery.getExcludedCards())) {
            query = and(query, not(existsIn(
                    deckCardIndex.getRepository(),
                    DeckSummary.ID_ATTRIBUTE,
                    DeckCard.DECK_ID_ATTRIBUTE,
                    in(DeckCard.CARD_ID_ATTRIBUTE, deckQuery.getExcludedCards()))));
        }
        if (deckQuery.getMinPrice() != null || deckQuery.getMaxPrice() != null) {
            query = and(query, has(DeckSummary.PRICE_ATTRIBUTE));
            if (deckQuery.getMinPrice() != null) {
                query = and(query, greaterThanOrEqualTo(DeckSummary.PRICE_ATTRIBUTE, deckQuery.getMinPrice()));
            }
            if (deckQuery.getMaxPrice() != null) {
                query = and(query, lessThanOrEqualTo(DeckSummary.PRICE_ATTRIBUTE, deckQuery.getMaxPrice()));
            }
        }
        if (StringUtils.isNotBlank(deckQuery.getCardText())) {
            List<Integer> ids = new ArrayList<>();
            try (ResultSet<Crypt> crypts = cryptCache.selectAll(null, deckQuery.getCardText())) {
                for (Crypt crypt : crypts) {
                    ids.add(crypt.getId());
                }
            }
            try (ResultSet<Library> libraries = libraryCache.selectAll(null, deckQuery.getCardText())) {
                for (Library library : libraries) {
                    ids.add(library.getId());
                }
            }
            query = and(query, existsIn(
                    deckCardIndex.getRepository(),
                    DeckSummary.ID_ATTRIBUTE,
                    DeckCard.DECK_ID_ATTRIBUTE,
                    in(DeckCard.CARD_ID_ATTRIBUTE, ids)));
        }
        if (deckQuery.isStarVampire()) {
            query = and(query, existsIn(
                    deckCardIndex.getRepository(),
                    DeckSummary.ID_ATTRIBUTE,
                    DeckCard.DECK_ID_ATTRIBUTE,
                    QueryFactory.and(equal(DeckCard.IS_CRYPT_ATTRIBUTE, true), greaterThanOrEqualTo(DeckCard.NUMBER_ATTRIBUTE, CRYPT_MAIN_MIN_NUMBER))));
        }
        if (CollectionUtils.isNotEmpty(deckQuery.getClans())) {
            if (deckQuery.isClanAny()) {
                Query<DeckSummary> clanQuery = null;
                for (String clan : deckQuery.getClans()) {
                    clanQuery = or(clanQuery, in(DeckSummary.CLAN_MULTI_ATTRIBUTE, clan));
                }
                query = and(query, clanQuery);
            } else {
                for (String clan : deckQuery.getClans()) {
                    query = and(query, in(DeckSummary.CLAN_MULTI_ATTRIBUTE, clan));
                }
            }
        }
        if (CollectionUtils.isNotEmpty(deckQuery.getNotClans())) {
            for (String clan : deckQuery.getNotClans()) {
                query = and(query, not(in(DeckSummary.CLAN_MULTI_ATTRIBUTE, clan)));
            }
        }
        if (deckQuery.isSingleClan()) {
            query = and(query, equal(DeckSummary.CLAN_NUMBER_ATTRIBUTE, 1));
        }
        if (CollectionUtils.isNotEmpty(deckQuery.getDisciplines())) {
            if (deckQuery.isDisciplineAny()) {
                Query<DeckSummary> disciplineQuery = null;
                for (String discipline : deckQuery.getDisciplines()) {
                    disciplineQuery = or(disciplineQuery, in(DeckSummary.DISCIPLINE_MULTI_ATTRIBUTE, discipline));
                }
                query = and(query, disciplineQuery);
            } else {
                for (String discipline : deckQuery.getDisciplines()) {
                    query = and(query, in(DeckSummary.DISCIPLINE_MULTI_ATTRIBUTE, discipline));
                }
            }
        }
        if (CollectionUtils.isNotEmpty(deckQuery.getNotDisciplines())) {
            for (String discipline : deckQuery.getNotDisciplines()) {
                query = and(query, not(in(DeckSummary.DISCIPLINE_MULTI_ATTRIBUTE, discipline)));
            }
        }
        if (deckQuery.isSingleDiscipline()) {
            query = and(query, equal(DeckSummary.DISCIPLINE_NUMBER_ATTRIBUTE, 1));
        }
        if (deckQuery.getCryptSizeMin() != null) {
            query = and(query, greaterThanOrEqualTo(DeckSummary.CRYPT_SIZE_ATTRIBUTE, deckQuery.getCryptSizeMin()));
        }
        if (deckQuery.getCryptSizeMax() != null) {
            query = and(query, lessThanOrEqualTo(DeckSummary.CRYPT_SIZE_ATTRIBUTE, deckQuery.getCryptSizeMax()));
        }
        if (deckQuery.getLibrarySizeMin() != null) {
            query = and(query, greaterThanOrEqualTo(DeckSummary.LIBRARY_SIZE_ATTRIBUTE, deckQuery.getLibrarySizeMin()));
        }
        if (deckQuery.getLibrarySizeMax() != null) {
            query = and(query, lessThanOrEqualTo(DeckSummary.LIBRARY_SIZE_ATTRIBUTE, deckQuery.getLibrarySizeMax()));
        }
        if (deckQuery.getType() != null) {
            if (deckQuery.getUserId() != null && deckQuery.getType() == DeckType.USER) {
                query = and(query, equal(DeckSummary.USER_ATTRIBUTE, deckQuery.getUserId()));
            } else {
                query = and(query, equal(DeckSummary.TYPE_ATTRIBUTE, deckQuery.getType()));
            }
        } else {
            query = and(query, in(DeckSummary.TYPE_ATTRIBUTE, ALL_DECK_TYPES));
        }
        if (deckQuery.getName() != null) {
            query = and(query, contains(DeckSummary.NAME_ATTRIBUTE, StringUtils.lowerCase(deckQuery.getName())));
        }
        if (deckQuery.getAuthor() != null) {
            if (Boolean.TRUE.equals(deckQuery.getExactAuthor())) {
                query = and(query, equal(DeckSummary.AUTHOR_ATTRIBUTE, StringUtils.lowerCase(deckQuery.getAuthor())));
            } else {
                query = and(query, contains(DeckSummary.AUTHOR_ATTRIBUTE, StringUtils.lowerCase(deckQuery.getAuthor())));
            }
        }
        if (StringUtils.isNotBlank(deckQuery.getTournament())) {
            query = and(query, contains(DeckSummary.TOURNAMENT_ATTRIBUTE, StringUtils.lowerCase(deckQuery.getTournament())));
        }
        if (StringUtils.isNotBlank(deckQuery.getPlace())) {
            String place = StringUtils.lowerCase(deckQuery.getPlace());
            query = and(query, or(contains(DeckSummary.PLACE_ATTRIBUTE, place), contains(DeckSummary.COUNTRY_ATTRIBUTE, place)));
        }
        if (CollectionUtils.isNotEmpty(deckQuery.getRounds())) {
            query = and(query, in(DeckSummary.ROUNDS_ATTRIBUTE, deckQuery.getRounds()));
        }
        if (deckQuery.getGroup() != null) {
            Query<DeckSummary> groupQuery = null;
            for (Integer group : deckQuery.getGroup()) {
                groupQuery = or(groupQuery, in(DeckSummary.GROUP_MULTI_ATTRIBUTE, group));
            }
            query = and(query, groupQuery);
        }
        if (deckQuery.getMaxYear() != null) {
            query = and(query, lessThanOrEqualTo(DeckSummary.YEAR_ATTRIBUTE, deckQuery.getMaxYear()));
        }
        if (deckQuery.getMinYear() != null) {
            query = and(query, greaterThanOrEqualTo(DeckSummary.YEAR_ATTRIBUTE, deckQuery.getMinYear()));
        }
        if (deckQuery.getMaxPlayers() != null) {
            query = and(query, lessThanOrEqualTo(DeckSummary.PLAYERS_ATTRIBUTE, deckQuery.getMaxPlayers()));
        }
        if (deckQuery.getMinPlayers() != null) {
            query = and(query, greaterThanOrEqualTo(DeckSummary.PLAYERS_ATTRIBUTE, deckQuery.getMinPlayers()));
        }
        if (deckQuery.getMaster() != null) {
            if (deckQuery.getProportionType() == DeckQuery.ProportionType.ABSOLUTE) {
                query = and(query, cardPercentage(deckQuery.getMaster(), DeckSummary.MASTER_ABSOLUTE_ATTRIBUTE));
            } else {
                query = and(query, cardPercentage(deckQuery.getMaster(), DeckSummary.MASTER_PERCENTAGE_ATTRIBUTE));
            }
        }
        if (deckQuery.getAction() != null) {
            if (deckQuery.getProportionType() == DeckQuery.ProportionType.ABSOLUTE) {
                query = and(query, cardPercentage(deckQuery.getAction(), DeckSummary.ACTION_ABSOLUTE_ATTRIBUTE));
            } else {
                query = and(query, cardPercentage(deckQuery.getAction(), DeckSummary.ACTION_PERCENTAGE_ATTRIBUTE));
            }
        }
        if (deckQuery.getPolitical() != null) {
            if (deckQuery.getProportionType() == DeckQuery.ProportionType.ABSOLUTE) {
                query = and(query, cardPercentage(deckQuery.getPolitical(), DeckSummary.POLITICAL_ABSOLUTE_ATTRIBUTE));
            } else {
                query = and(query, cardPercentage(deckQuery.getPolitical(), DeckSummary.POLITICAL_PERCENTAGE_ATTRIBUTE));
            }
        }
        if (deckQuery.getRetainer() != null) {
            if (deckQuery.getProportionType() == DeckQuery.ProportionType.ABSOLUTE) {
                query = and(query, cardPercentage(deckQuery.getRetainer(), DeckSummary.RETAINER_ABSOLUTE_ATTRIBUTE));
            } else {
                query = and(query, cardPercentage(deckQuery.getRetainer(), DeckSummary.RETAINER_PERCENTAGE_ATTRIBUTE));
            }
        }
        if (deckQuery.getEquipment() != null) {
            if (deckQuery.getProportionType() == DeckQuery.ProportionType.ABSOLUTE) {
                query = and(query, cardPercentage(deckQuery.getEquipment(), DeckSummary.EQUIPMENT_ABSOLUTE_ATTRIBUTE));
            } else {
                query = and(query, cardPercentage(deckQuery.getEquipment(), DeckSummary.EQUIPMENT_PERCENTAGE_ATTRIBUTE));
            }
        }
        if (deckQuery.getAlly() != null) {
            if (deckQuery.getProportionType() == DeckQuery.ProportionType.ABSOLUTE) {
                query = and(query, cardPercentage(deckQuery.getAlly(), DeckSummary.ALLY_ABSOLUTE_ATTRIBUTE));
            } else {
                query = and(query, cardPercentage(deckQuery.getAlly(), DeckSummary.ALLY_PERCENTAGE_ATTRIBUTE));
            }
        }
        if (deckQuery.getModifier() != null) {
            if (deckQuery.getProportionType() == DeckQuery.ProportionType.ABSOLUTE) {
                query = and(query, cardPercentage(deckQuery.getModifier(), DeckSummary.MODIFIER_ABSOLUTE_ATTRIBUTE));
            } else {
                query = and(query, cardPercentage(deckQuery.getModifier(), DeckSummary.MODIFIER_PERCENTAGE_ATTRIBUTE));
            }
        }
        if (deckQuery.getCombat() != null) {
            if (deckQuery.getProportionType() == DeckQuery.ProportionType.ABSOLUTE) {
                query = and(query, cardPercentage(deckQuery.getCombat(), DeckSummary.COMBAT_ABSOLUTE_ATTRIBUTE));
            } else {
                query = and(query, cardPercentage(deckQuery.getCombat(), DeckSummary.COMBAT_PERCENTAGE_ATTRIBUTE));
            }
        }
        if (deckQuery.getReaction() != null) {
            if (deckQuery.getProportionType() == DeckQuery.ProportionType.ABSOLUTE) {
                query = and(query, cardPercentage(deckQuery.getReaction(), DeckSummary.REACTION_ABSOLUTE_ATTRIBUTE));
            } else {
                query = and(query, cardPercentage(deckQuery.getReaction(), DeckSummary.REACTION_PERCENTAGE_ATTRIBUTE));
            }
        }
        if (deckQuery.getEvent() != null) {
            if (deckQuery.getProportionType() == DeckQuery.ProportionType.ABSOLUTE) {
                query = and(query, cardPercentage(deckQuery.getEvent(), DeckSummary.EVENT_ABSOLUTE_ATTRIBUTE));
            } else {
                query = and(query, cardPercentage(deckQuery.getEvent(), DeckSummary.EVENT_PERCENTAGE_ATTRIBUTE));
            }
        }
        if (CollectionUtils.isNotEmpty(deckQuery.getTags())) {
            for (String tag : deckQuery.getTags()) {
                query = and(query, in(DeckSummary.TAG_MULTI_ATTRIBUTE, tag));
            }
        }
        if (deckQuery.isFavorite() && deckQuery.getUserId() != null) {
            query = and(query, in(DeckSummary.FAVORITE_MULTI_ATTRIBUTE, deckQuery.getUserId()));
        }
        if (deckQuery.isDetailed()) {
            query = and(query, equal(DeckSummary.DETAILED_ATTRIBUTE, Boolean.TRUE));
        }
        if (deckQuery.getLimitedFormat() != null) {
            query = and(query, contains(DeckSummary.LIMITED_FORMAT_ATTRIBUTE, StringUtils.lowerCase(deckQuery.getLimitedFormat())));
        }
        if (CollectionUtils.isNotEmpty(deckQuery.getPaths())) {
            query = and(query, in(DeckSummary.PATH_ATTRIBUTE, deckQuery.getPaths()));
        }
        if (deckQuery.getArchetype() != null) {
            if (deckQuery.getArchetype() == 0) {
                query = and(query, not(has(DeckSummary.ARCHETYPE_ATTRIBUTE)));
            } else {
                query = and(query, equal(DeckSummary.ARCHETYPE_ATTRIBUTE, deckQuery.getArchetype()));
            }
        }
        if (deckQuery.getCreationDate() != null) {
            Long creationTimestamp = deckQuery.getCreationDate()
                    .atStartOfDay()
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
            query = and(query, greaterThanOrEqualTo(DeckSummary.CREATION_TIMESTAMP_ATTRIBUTE, creationTimestamp));
        }
        if (log.isDebugEnabled()) {
            log.debug("Query {} with options {}", query, queryOptions);
        }
        return decks.retrieve(query, queryOptions);
    }


    private Query<DeckSummary> cardPercentage(DeckQuery.CardProportion percentage, Attribute<DeckSummary, Integer> attributeFilter) {
        return and(greaterThanOrEqualTo(attributeFilter, percentage.getMin()), lessThanOrEqualTo(attributeFilter, percentage.getMax()));
    }

    private Query<DeckSummary> and(Query<DeckSummary> first, Query<DeckSummary> second) {
        return first != null ? QueryFactory.and(first, second) : second;
    }

    private Query<DeckSummary> or(Query<DeckSummary> first, Query<DeckSummary> second) {
        return first != null ? QueryFactory.or(first, second) : second;
    }

}
