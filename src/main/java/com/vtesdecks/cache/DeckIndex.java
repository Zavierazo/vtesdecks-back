package com.vtesdecks.cache;

import com.google.common.collect.Lists;
import com.googlecode.cqengine.ConcurrentIndexedCollection;
import com.googlecode.cqengine.IndexedCollection;
import com.googlecode.cqengine.attribute.Attribute;
import com.googlecode.cqengine.index.hash.HashIndex;
import com.googlecode.cqengine.index.unique.UniqueIndex;
import com.googlecode.cqengine.query.Query;
import com.googlecode.cqengine.query.QueryFactory;
import com.googlecode.cqengine.query.option.DeduplicationOption;
import com.googlecode.cqengine.query.option.DeduplicationStrategy;
import com.googlecode.cqengine.query.option.QueryOptions;
import com.googlecode.cqengine.query.option.Thresholds;
import com.googlecode.cqengine.query.simple.Equal;
import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.cache.factory.DeckFactory;
import com.vtesdecks.cache.indexable.Crypt;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.cache.indexable.DeckCard;
import com.vtesdecks.cache.indexable.Library;
import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.enums.CacheEnum;
import com.vtesdecks.jpa.entity.DeckEntity;
import com.vtesdecks.jpa.entity.LimitedFormatEntity;
import com.vtesdecks.jpa.repositories.DeckRepository;
import com.vtesdecks.jpa.repositories.LimitedFormatRepository;
import com.vtesdecks.model.DeckQuery;
import com.vtesdecks.model.limitedformat.LimitedFormatPayload;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
import static com.googlecode.cqengine.query.QueryFactory.orderBy;
import static com.googlecode.cqengine.query.QueryFactory.queryOptions;
import static com.googlecode.cqengine.query.QueryFactory.threshold;
import static com.googlecode.cqengine.query.option.EngineThresholds.INDEX_ORDERING_SELECTIVITY;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DeckIndex implements Runnable {
    private static final List<DeckType> ALL_DECK_TYPES = Lists.newArrayList(DeckType.TOURNAMENT, DeckType.COMMUNITY);
    private static final int CRYPT_MAIN_MIN_NUMBER = 4;
    private static final int CRYPT_SINGLE_MIN_NUMBER = 8;
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
    private IndexedCollection<Deck> decks = new ConcurrentIndexedCollection<Deck>();
    private final BlockingQueue<String> refreshQueue = new LinkedBlockingQueue<>();
    private boolean keepRunning = true;


    @PostConstruct
    public void setUp() {
        //Id is always unique and is the Primary Key
        decks.addIndex(UniqueIndex.onAttribute(Deck.ID_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(Deck.PUBLISHED_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(Deck.TYPE_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(Deck.NAME_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(Deck.USER_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(Deck.AUTHOR_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(Deck.RATE_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(Deck.VOTES_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(Deck.VIEWS_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(Deck.PLAYERS_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(Deck.YEAR_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(Deck.VIEWS_LAST_MONTH_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(Deck.COMMENTS_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(Deck.CREATION_DATE_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(Deck.MODIFY_DATE_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(Deck.CLAN_MULTI_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(Deck.DISCIPLINE_MULTI_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(Deck.GROUP_MULTI_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(Deck.CLAN_NUMBER_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(Deck.DISCIPLINE_NUMBER_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(Deck.MASTER_PERCENTAGE_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(Deck.ACTION_PERCENTAGE_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(Deck.POLITICAL_PERCENTAGE_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(Deck.RETAINER_PERCENTAGE_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(Deck.EQUIPMENT_PERCENTAGE_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(Deck.ALLY_PERCENTAGE_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(Deck.MODIFIER_PERCENTAGE_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(Deck.COMBAT_PERCENTAGE_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(Deck.REACTION_PERCENTAGE_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(Deck.EVENT_PERCENTAGE_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(Deck.MASTER_ABSOLUTE_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(Deck.ACTION_ABSOLUTE_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(Deck.POLITICAL_ABSOLUTE_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(Deck.RETAINER_ABSOLUTE_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(Deck.EQUIPMENT_ABSOLUTE_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(Deck.ALLY_ABSOLUTE_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(Deck.MODIFIER_ABSOLUTE_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(Deck.COMBAT_ABSOLUTE_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(Deck.REACTION_ABSOLUTE_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(Deck.EVENT_ABSOLUTE_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(Deck.TAG_MULTI_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(Deck.FAVORITE_MULTI_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(Deck.LIMITED_FORMAT_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(Deck.PATH_ATTRIBUTE));
        decks.addIndex(HashIndex.onAttribute(Deck.PRICE_ATTRIBUTE));
        Thread workerThread = new Thread(this);
        workerThread.setDaemon(true);
        workerThread.start();
    }


    @Override
    public void run() {
        while (keepRunning) {
            try {
                refreshIndex(refreshQueue.take());
            } catch (final InterruptedException e) {
                log.error("Blocking queue was interrupted {}", e);
                // Restore interrupted state...
                Thread.currentThread().interrupt();
            } catch (final Exception e) {
                log.error("Unhandled exception: {} ", e);
            }
        }
    }


    @Scheduled(cron = "${jobs.scrappingDecks:0 0 * * * *}")
    public void refreshIndex() {
        StopWatch stopWatch = new StopWatch();
        ExecutorService executor = null;
        try {
            stopWatch.start();
            Set<String> currentKeys = decks.stream().map(Deck::getId).collect(Collectors.toSet());
            executor = Executors.newFixedThreadPool(50);
            List<LimitedFormatPayload> limitedFormats = getLimitedFormats();
            for (DeckEntity deck : deckRepository.findAll()) {
                if (Boolean.FALSE.equals(deck.getDeleted())) {
                    executor.execute(() -> refreshDeck(deck, limitedFormats));
                    currentKeys.remove(deck.getId());
                }
            }
            if (!currentKeys.isEmpty()) {
                log.warn("Deleting form index decks {}", currentKeys);
                for (String deleteKeys : currentKeys) {
                    Deck deck = get(deleteKeys);
                    decks.remove(deck);
                    deckCardIndex.removeDeck(deck.getId());
                }
            }
        } finally {
            if (executor != null) {
                try {
                    executor.shutdown();
                    if (!executor.awaitTermination(5, TimeUnit.MINUTES)) {
                        executor.shutdownNow();
                    }
                } catch (Exception e) {
                    log.error("Unable to finish execution", e);
                    executor.shutdownNow();
                }
            }
            stopWatch.stop();
            log.info("Index finished in {} ms. Colletion size is {}", stopWatch.lastTaskInfo().getTimeMillis(), decks.size());
        }
    }

    private void refreshIndex(String deckId) {
        Optional<DeckEntity> deck = deckRepository.findById(deckId);
        if (deck.isPresent() && Boolean.FALSE.equals(deck.get().getDeleted())) {
            refreshDeck(deck.get(), getLimitedFormats());
        } else {
            Deck deletedDeck = get(deckId);
            if (deletedDeck != null) {
                decks.remove(deletedDeck);
                deckCardIndex.removeDeck(deletedDeck.getId());
            }
        }
    }

    private void refreshDeck(DeckEntity deck, List<LimitedFormatPayload> limitedFormats) {
        try {
            List<DeckCard> deckCards = deckCardIndex.refreshIndex(deck.getId());
            Deck newDeck = deckFactory.getDeck(deck, deckCards, limitedFormats);
            syncDeck(deck, newDeck);
        } catch (Exception e) {
            log.error("Error when refresh deck {}", deck.getId(), e);
        }
    }

    private synchronized void syncDeck(DeckEntity deck, Deck newDeck) {
        Deck oldDeck = get(deck.getId());
        if (oldDeck != null && !oldDeck.equals(newDeck)) {
            decks.update(Lists.newArrayList(oldDeck), Lists.newArrayList(newDeck));
        } else if (oldDeck == null) {
            decks.add(newDeck);
        }
    }

    private List<LimitedFormatPayload> getLimitedFormats() {
        List<LimitedFormatEntity> limitedFormatEntities = limitedFormatRepository.findAll();
        return limitedFormatEntities.stream().map(LimitedFormatEntity::getFormat).toList();
    }

    public void enqueueRefreshIndex(String deckId) {
        refreshQueue.add(deckId);
    }


    public Deck get(String id) {
        Query<Deck> findByKeyQuery = equal(Deck.ID_ATTRIBUTE, id);
        ResultSet<Deck> result = decks.retrieve(findByKeyQuery);
        return (result.size() >= 1) ? result.uniqueResult() : null;
    }

    @Cacheable(value = CacheEnum.GENERIC, key = "'countByType'+#a0")
    public int countByType(DeckType deckType) {
        Query<Deck> query = equal(Deck.TYPE_ATTRIBUTE, deckType);
        ResultSet<Deck> results = decks.retrieve(query);
        return results.size();
    }

    public ResultSet<Deck> selectAll(DeckQuery deckQuery) {
        DeduplicationOption deduplication = deduplicate(DeduplicationStrategy.MATERIALIZE);
        Thresholds threshold = applyThresholds(threshold(INDEX_ORDERING_SELECTIVITY, 1.0));
        QueryOptions queryOptions;
        Query<Deck> query = null;
        switch (deckQuery.getOrder()) {
            case NAME:
                queryOptions = queryOptions(orderBy(ascending(Deck.NAME_ATTRIBUTE),
                        descending(Deck.CREATION_DATE_ATTRIBUTE)), threshold, deduplication);
                break;
            case VOTES:
                queryOptions = queryOptions(orderBy(descending(Deck.VOTES_ATTRIBUTE),
                        descending(Deck.RATE_ATTRIBUTE),
                        descending(Deck.CREATION_DATE_ATTRIBUTE)), threshold, deduplication);
                break;
            case RATE:
                queryOptions = queryOptions(orderBy(descending(Deck.RATE_ATTRIBUTE),
                        descending(Deck.VOTES_ATTRIBUTE),
                        descending(Deck.CREATION_DATE_ATTRIBUTE)), threshold, deduplication);
                break;
            case VIEWS:
                queryOptions = queryOptions(orderBy(descending(Deck.VIEWS_ATTRIBUTE),
                        descending(Deck.CREATION_DATE_ATTRIBUTE)), threshold, deduplication);
                break;
            case COMMENTS:
                queryOptions = queryOptions(orderBy(descending(Deck.COMMENTS_ATTRIBUTE),
                        descending(Deck.CREATION_DATE_ATTRIBUTE)), threshold, deduplication);
                break;
            case MODIFIED:
                queryOptions = queryOptions(orderBy(descending(Deck.MODIFY_DATE_ATTRIBUTE)), threshold, deduplication);
                break;
            case OLDEST:
                queryOptions = queryOptions(orderBy(ascending(Deck.CREATION_DATE_ATTRIBUTE)), threshold, deduplication);
                break;
            case POPULAR:
                queryOptions = queryOptions(orderBy(descending(Deck.VIEWS_LAST_MONTH_ATTRIBUTE),
                                descending(Deck.VIEWS_ATTRIBUTE),
                                descending(Deck.RATE_ATTRIBUTE),
                                descending(Deck.CREATION_DATE_ATTRIBUTE)),
                        threshold,
                        deduplication);
                break;
            case PLAYERS:
                queryOptions = queryOptions(orderBy(descending(Deck.PLAYERS_ATTRIBUTE),
                        descending(Deck.CREATION_DATE_ATTRIBUTE)), threshold, deduplication);
                break;
            case CHEAPEST:
                query = and(query, has(Deck.PRICE_ATTRIBUTE));
                queryOptions = queryOptions(orderBy(ascending(Deck.PRICE_ATTRIBUTE), descending(Deck.CREATION_DATE_ATTRIBUTE)), threshold, deduplication);
                break;
            case EXPENSIVE:
                query = and(query, has(Deck.PRICE_ATTRIBUTE));
                queryOptions = queryOptions(orderBy(descending(Deck.PRICE_ATTRIBUTE), descending(Deck.CREATION_DATE_ATTRIBUTE)), threshold, deduplication);
                break;
            case NEWEST:
            default:
                queryOptions = queryOptions(orderBy(descending(Deck.CREATION_DATE_ATTRIBUTE)), threshold, deduplication);
        }
        Equal<Deck, Boolean> published = QueryFactory.equal(Deck.PUBLISHED_ATTRIBUTE, true);
        if (deckQuery.getUser() != null) {
            query = and(query, or(equal(Deck.USER_ATTRIBUTE, deckQuery.getUser()), published));
        } else {
            query = and(query, published);
        }
        if (deckQuery.getCards() != null && !deckQuery.getCards().isEmpty()) {
            for (Map.Entry<Integer, Integer> card : deckQuery.getCards().entrySet()) {
                Integer cardId = card.getKey();
                Integer cardNumber = card.getValue();
                query = and(query, existsIn(
                        deckCardIndex.getRepository(),
                        Deck.ID_ATTRIBUTE,
                        DeckCard.DECK_ID_ATTRIBUTE,
                        QueryFactory.and(in(DeckCard.CARD_ID_ATTRIBUTE, cardId),
                                greaterThanOrEqualTo(DeckCard.NUMBER_ATTRIBUTE, cardNumber))));
            }
        }
        if (StringUtils.isNotBlank(deckQuery.getCardText())) {
            List<Integer> ids = new ArrayList<>();
            ResultSet<Crypt> crypts = cryptCache.selectAll(null, deckQuery.getCardText());
            for (Crypt crypt : crypts) {
                ids.add(crypt.getId());
            }
            ResultSet<Library> libraries = libraryCache.selectAll(null, deckQuery.getCardText());
            for (Library library : libraries) {
                ids.add(library.getId());
            }
            query = and(query, existsIn(
                    deckCardIndex.getRepository(),
                    Deck.ID_ATTRIBUTE,
                    DeckCard.DECK_ID_ATTRIBUTE,
                    in(DeckCard.CARD_ID_ATTRIBUTE, ids)));
        }
        if (deckQuery.isStarVampire()) {
            query = and(query, existsIn(
                    deckCardIndex.getRepository(),
                    Deck.ID_ATTRIBUTE,
                    DeckCard.DECK_ID_ATTRIBUTE,
                    QueryFactory.and(equal(DeckCard.IS_CRYPT_ATTRIBUTE, true), greaterThanOrEqualTo(DeckCard.NUMBER_ATTRIBUTE, CRYPT_MAIN_MIN_NUMBER))));
        }
        if (CollectionUtils.isNotEmpty(deckQuery.getClans())) {
            for (String clan : deckQuery.getClans()) {
                query = and(query, in(Deck.CLAN_MULTI_ATTRIBUTE, clan));
            }
        }
        if (deckQuery.isSingleClan()) {
            query = and(query, equal(Deck.CLAN_NUMBER_ATTRIBUTE, 1));
        }
        if (CollectionUtils.isNotEmpty(deckQuery.getDisciplines())) {
            for (String discipline : deckQuery.getDisciplines()) {
                query = and(query, in(Deck.DISCIPLINE_MULTI_ATTRIBUTE, discipline));
            }
        }
        if (deckQuery.isSingleDiscipline()) {
            query = and(query, equal(Deck.DISCIPLINE_NUMBER_ATTRIBUTE, 1));
        }
        if (deckQuery.getCryptSizeMin() != null) {
            query = and(query, greaterThanOrEqualTo(Deck.CRYPT_SIZE_ATTRIBUTE, deckQuery.getCryptSizeMin()));
        }
        if (deckQuery.getCryptSizeMax() != null) {
            query = and(query, lessThanOrEqualTo(Deck.CRYPT_SIZE_ATTRIBUTE, deckQuery.getCryptSizeMax()));
        }
        if (deckQuery.getLibrarySizeMin() != null) {
            query = and(query, greaterThanOrEqualTo(Deck.LIBRARY_SIZE_ATTRIBUTE, deckQuery.getLibrarySizeMin()));
        }
        if (deckQuery.getLibrarySizeMax() != null) {
            query = and(query, lessThanOrEqualTo(Deck.LIBRARY_SIZE_ATTRIBUTE, deckQuery.getLibrarySizeMax()));
        }
        if (deckQuery.getType() != null) {
            if (deckQuery.getUser() != null && deckQuery.getType() == DeckType.USER) {
                query = and(query, equal(Deck.USER_ATTRIBUTE, deckQuery.getUser()));
            } else {
                query = and(query, equal(Deck.TYPE_ATTRIBUTE, deckQuery.getType()));
            }
        } else {
            query = and(query, in(Deck.TYPE_ATTRIBUTE, ALL_DECK_TYPES));
        }
        if (deckQuery.getName() != null) {
            query = and(query, contains(Deck.NAME_ATTRIBUTE, StringUtils.lowerCase(deckQuery.getName())));
        }
        if (deckQuery.getAuthor() != null) {
            if (Boolean.TRUE.equals(deckQuery.getExactAuthor())) {
                query = and(query, equal(Deck.AUTHOR_ATTRIBUTE, StringUtils.lowerCase(deckQuery.getAuthor())));
            } else {
                query = and(query, contains(Deck.AUTHOR_ATTRIBUTE, StringUtils.lowerCase(deckQuery.getAuthor())));
            }
        }
        if (deckQuery.getGroups() != null) {
            Query<Deck> groupQuery = null;
            for (Integer group : deckQuery.getGroups()) {
                groupQuery = or(groupQuery, in(Deck.GROUP_MULTI_ATTRIBUTE, group));
            }
            query = and(query, groupQuery);
        }
        if (deckQuery.getMaxYear() != null) {
            query = and(query, lessThanOrEqualTo(Deck.YEAR_ATTRIBUTE, deckQuery.getMaxYear()));
        }
        if (deckQuery.getMinYear() != null) {
            query = and(query, greaterThanOrEqualTo(Deck.YEAR_ATTRIBUTE, deckQuery.getMinYear()));
        }
        if (deckQuery.getMaxPlayers() != null) {
            query = and(query, lessThanOrEqualTo(Deck.PLAYERS_ATTRIBUTE, deckQuery.getMaxPlayers()));
        }
        if (deckQuery.getMinPlayers() != null) {
            query = and(query, greaterThanOrEqualTo(Deck.PLAYERS_ATTRIBUTE, deckQuery.getMinPlayers()));
        }
        if (deckQuery.getMaster() != null) {
            if (deckQuery.getProportionType() == DeckQuery.ProportionType.ABSOLUTE) {
                query = and(query, cardPercentage(deckQuery.getMaster(), Deck.MASTER_ABSOLUTE_ATTRIBUTE));
            } else {
                query = and(query, cardPercentage(deckQuery.getMaster(), Deck.MASTER_PERCENTAGE_ATTRIBUTE));
            }
        }
        if (deckQuery.getAction() != null) {
            if (deckQuery.getProportionType() == DeckQuery.ProportionType.ABSOLUTE) {
                query = and(query, cardPercentage(deckQuery.getAction(), Deck.ACTION_ABSOLUTE_ATTRIBUTE));
            } else {
                query = and(query, cardPercentage(deckQuery.getAction(), Deck.ACTION_PERCENTAGE_ATTRIBUTE));
            }
        }
        if (deckQuery.getPolitical() != null) {
            if (deckQuery.getProportionType() == DeckQuery.ProportionType.ABSOLUTE) {
                query = and(query, cardPercentage(deckQuery.getPolitical(), Deck.POLITICAL_ABSOLUTE_ATTRIBUTE));
            } else {
                query = and(query, cardPercentage(deckQuery.getPolitical(), Deck.POLITICAL_PERCENTAGE_ATTRIBUTE));
            }
        }
        if (deckQuery.getRetainer() != null) {
            if (deckQuery.getProportionType() == DeckQuery.ProportionType.ABSOLUTE) {
                query = and(query, cardPercentage(deckQuery.getRetainer(), Deck.RETAINER_ABSOLUTE_ATTRIBUTE));
            } else {
                query = and(query, cardPercentage(deckQuery.getRetainer(), Deck.RETAINER_PERCENTAGE_ATTRIBUTE));
            }
        }
        if (deckQuery.getEquipment() != null) {
            if (deckQuery.getProportionType() == DeckQuery.ProportionType.ABSOLUTE) {
                query = and(query, cardPercentage(deckQuery.getEquipment(), Deck.EQUIPMENT_ABSOLUTE_ATTRIBUTE));
            } else {
                query = and(query, cardPercentage(deckQuery.getEquipment(), Deck.EQUIPMENT_PERCENTAGE_ATTRIBUTE));
            }
        }
        if (deckQuery.getAlly() != null) {
            if (deckQuery.getProportionType() == DeckQuery.ProportionType.ABSOLUTE) {
                query = and(query, cardPercentage(deckQuery.getAlly(), Deck.ALLY_ABSOLUTE_ATTRIBUTE));
            } else {
                query = and(query, cardPercentage(deckQuery.getAlly(), Deck.ALLY_PERCENTAGE_ATTRIBUTE));
            }
        }
        if (deckQuery.getModifier() != null) {
            if (deckQuery.getProportionType() == DeckQuery.ProportionType.ABSOLUTE) {
                query = and(query, cardPercentage(deckQuery.getModifier(), Deck.MODIFIER_ABSOLUTE_ATTRIBUTE));
            } else {
                query = and(query, cardPercentage(deckQuery.getModifier(), Deck.MODIFIER_PERCENTAGE_ATTRIBUTE));
            }
        }
        if (deckQuery.getCombat() != null) {
            if (deckQuery.getProportionType() == DeckQuery.ProportionType.ABSOLUTE) {
                query = and(query, cardPercentage(deckQuery.getCombat(), Deck.COMBAT_ABSOLUTE_ATTRIBUTE));
            } else {
                query = and(query, cardPercentage(deckQuery.getCombat(), Deck.COMBAT_PERCENTAGE_ATTRIBUTE));
            }
        }
        if (deckQuery.getReaction() != null) {
            if (deckQuery.getProportionType() == DeckQuery.ProportionType.ABSOLUTE) {
                query = and(query, cardPercentage(deckQuery.getReaction(), Deck.REACTION_ABSOLUTE_ATTRIBUTE));
            } else {
                query = and(query, cardPercentage(deckQuery.getReaction(), Deck.REACTION_PERCENTAGE_ATTRIBUTE));
            }
        }
        if (deckQuery.getEvent() != null) {
            if (deckQuery.getProportionType() == DeckQuery.ProportionType.ABSOLUTE) {
                query = and(query, cardPercentage(deckQuery.getEvent(), Deck.EVENT_ABSOLUTE_ATTRIBUTE));
            } else {
                query = and(query, cardPercentage(deckQuery.getEvent(), Deck.EVENT_PERCENTAGE_ATTRIBUTE));
            }
        }
        if (CollectionUtils.isNotEmpty(deckQuery.getTags())) {
            for (String tag : deckQuery.getTags()) {
                query = and(query, in(Deck.TAG_MULTI_ATTRIBUTE, tag));
            }
        }
        if (deckQuery.isFavorite() && deckQuery.getUser() != null) {
            query = and(query, in(Deck.FAVORITE_MULTI_ATTRIBUTE, deckQuery.getUser()));
        }
        if (deckQuery.getLimitedFormat() != null) {
            query = and(query, contains(Deck.LIMITED_FORMAT_ATTRIBUTE, StringUtils.lowerCase(deckQuery.getLimitedFormat())));
        }
        if (CollectionUtils.isNotEmpty(deckQuery.getPaths())) {
            query = and(query, in(Deck.PATH_ATTRIBUTE, deckQuery.getPaths()));
        }
        if (log.isDebugEnabled()) {
            log.debug("Query {} with options {}", query, queryOptions);
        }
        return decks.retrieve(query, queryOptions);
    }


    private Query<Deck> cardPercentage(DeckQuery.CardProportion percentage, Attribute<Deck, Integer> attributeFilter) {
        return and(greaterThanOrEqualTo(attributeFilter, percentage.getMin()), lessThanOrEqualTo(attributeFilter, percentage.getMax()));
    }

    private Query<Deck> and(Query<Deck> first, Query<Deck> second) {
        return first != null ? QueryFactory.and(first, second) : second;
    }

    private Query<Deck> or(Query<Deck> first, Query<Deck> second) {
        return first != null ? QueryFactory.or(first, second) : second;
    }

}
