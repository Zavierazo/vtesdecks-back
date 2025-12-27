package com.vtesdecks.cache;

import com.google.common.collect.Lists;
import com.googlecode.cqengine.ConcurrentIndexedCollection;
import com.googlecode.cqengine.IndexedCollection;
import com.googlecode.cqengine.index.hash.HashIndex;
import com.googlecode.cqengine.index.unique.UniqueIndex;
import com.googlecode.cqengine.query.Query;
import com.googlecode.cqengine.query.QueryFactory;
import com.googlecode.cqengine.query.option.QueryOptions;
import com.googlecode.cqengine.query.option.Thresholds;
import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.cache.factory.LibraryFactory;
import com.vtesdecks.cache.indexable.Library;
import com.vtesdecks.jpa.entity.CardShopEntity;
import com.vtesdecks.jpa.entity.LibraryEntity;
import com.vtesdecks.jpa.entity.LibraryI18nEntity;
import com.vtesdecks.jpa.entity.extra.DeckCardCount;
import com.vtesdecks.jpa.repositories.CardShopRepository;
import com.vtesdecks.jpa.repositories.DeckCardRepository;
import com.vtesdecks.jpa.repositories.LibraryI18nRepository;
import com.vtesdecks.jpa.repositories.LibraryRepository;
import com.vtesdecks.model.LibraryTaint;
import com.vtesdecks.util.Utils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.googlecode.cqengine.query.QueryFactory.all;
import static com.googlecode.cqengine.query.QueryFactory.and;
import static com.googlecode.cqengine.query.QueryFactory.ascending;
import static com.googlecode.cqengine.query.QueryFactory.contains;
import static com.googlecode.cqengine.query.QueryFactory.descending;
import static com.googlecode.cqengine.query.QueryFactory.equal;
import static com.googlecode.cqengine.query.QueryFactory.in;
import static com.googlecode.cqengine.query.QueryFactory.orderBy;
import static com.googlecode.cqengine.query.QueryFactory.queryOptions;
import static com.googlecode.cqengine.query.QueryFactory.threshold;
import static com.googlecode.cqengine.query.option.EngineThresholds.INDEX_ORDERING_SELECTIVITY;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LibraryCache {
    private IndexedCollection<Library> cache = new ConcurrentIndexedCollection<Library>();
    @Autowired
    private LibraryRepository libraryRepository;
    @Autowired
    private LibraryI18nRepository libraryI18nRepository;
    @Autowired
    private DeckCardRepository deckCardRepository;
    @Autowired
    private CardShopRepository cardShopRepository;
    @Autowired
    private LibraryFactory libraryFactory;


    @PostConstruct
    public void setUp() {
        //Id is always unique and is the Primary Key
        cache.addIndex(UniqueIndex.onAttribute(Library.ID_ATTRIBUTE));
        cache.addIndex(HashIndex.onAttribute(Library.NAME_ATTRIBUTE));
        cache.addIndex(HashIndex.onAttribute(Library.TEXT_ATTRIBUTE));
        cache.addIndex(HashIndex.onAttribute(Library.TYPE_NUMBER_ATTRIBUTE));
        cache.addIndex(HashIndex.onAttribute(Library.TYPE_MULTI_ATTRIBUTE));
        cache.addIndex(HashIndex.onAttribute(Library.CLAN_NUMBER_ATTRIBUTE));
        cache.addIndex(HashIndex.onAttribute(Library.CLAN_MULTI_ATTRIBUTE));
        cache.addIndex(HashIndex.onAttribute(Library.DISCIPLINE_NUMBER_ATTRIBUTE));
        cache.addIndex(HashIndex.onAttribute(Library.DISCIPLINE_MULTI_ATTRIBUTE));
        cache.addIndex(HashIndex.onAttribute(Library.LAST_UPDATE_ATTRIBUTE));
    }


    @Scheduled(cron = "${jobs.cache.refresh:0 55 * * * *}")
    public void refreshIndex() {
        StopWatch stopWatch = new StopWatch();
        try {
            stopWatch.start();
            Set<Integer> currentKeys = cache.stream().map(Library::getId).collect(Collectors.toSet());
            List<DeckCardCount> countByCard = deckCardRepository.selectCountByCard();
            List<DeckCardCount> deckCountByCard = deckCardRepository.selectDeckCountByCard();
            List<CardShopEntity> cardShopList = cardShopRepository.findAll();
            List<LibraryI18nEntity> libraryI18nList = libraryI18nRepository.findAll();
            for (LibraryEntity library : libraryRepository.findAll()) {
                refreshIndex(library,
                        libraryI18nList.stream().filter(libraryI18n -> libraryI18n.getId().getCardId().equals(library.getId())).toList(),
                        cardShopList.stream().filter(cardShop -> cardShop.getCardId().equals(library.getId())).toList(),
                        deckCountByCard.stream().filter(count -> count.getId().equals(library.getId())).mapToLong(DeckCardCount::getNumberAsLong).sum(),
                        countByCard.stream().filter(count -> count.getId().equals(library.getId())).mapToLong(DeckCardCount::getNumberAsLong).sum());
                currentKeys.remove(library.getId());
            }
            if (!currentKeys.isEmpty()) {
                log.warn("Deleting form index {}", currentKeys);
                for (Integer key : currentKeys) {
                    cache.remove(key);
                }
            }
        } finally {
            stopWatch.stop();
            log.info("Index finished in {} ms. Colletion size is {}", stopWatch.lastTaskInfo().getTimeMillis(), cache.size());
        }
    }

    private void refreshIndex(LibraryEntity library, List<LibraryI18nEntity> libraryI18nList, List<CardShopEntity> cardShopList, Long deckCount, Long count) {
        try {
            Library oldLibrary = get(library.getId());
            Library newLibrary = libraryFactory.getLibrary(library, libraryI18nList, cardShopList);
            newLibrary.setDeckPopularity(deckCount);
            newLibrary.setCardPopularity(count);
            if (deckCount > 0) {
                newLibrary.getTaints().add(LibraryTaint.TWD.getName());
            }
            if (oldLibrary != null && !oldLibrary.equals(newLibrary)) {
                cache.update(Lists.newArrayList(oldLibrary), Lists.newArrayList(newLibrary));
            } else if (oldLibrary == null) {
                cache.add(newLibrary);
            }
        } catch (Exception e) {
            log.error("Error when refresh library {}", library.getId(), e);
        }
    }

    public Library get(Integer key) {
        Query<Library> findByKeyQuery = equal(Library.ID_ATTRIBUTE, key);
        ResultSet<Library> result = cache.retrieve(findByKeyQuery);
        return (result.size() >= 1) ? result.uniqueResult() : null;
    }

    public ResultSet<Library> selectByExactName(String name) {
        Thresholds threshold = QueryFactory.applyThresholds(threshold(INDEX_ORDERING_SELECTIVITY, 1.0));
        QueryOptions queryOptions = queryOptions(orderBy(ascending(Library.NAME_ATTRIBUTE)), threshold);
        Query<Library> query = equal(Library.NAME_ATTRIBUTE, Utils.normalizeName(StringUtils.lowerCase(name)));
        if (log.isDebugEnabled()) {
            log.debug("Query {} with options {}", query, queryOptions);
        }
        return cache.retrieve(query, queryOptions);
    }

    public ResultSet<Library> selectAll(String name, String text) {
        Thresholds threshold = QueryFactory.applyThresholds(threshold(INDEX_ORDERING_SELECTIVITY, 1.0));
        QueryOptions queryOptions = queryOptions(orderBy(ascending(Library.NAME_ATTRIBUTE)), threshold);
        Query<Library> query = all(Library.class);
        if (name != null) {
            query = and(query, contains(Library.NAME_ATTRIBUTE, Utils.normalizeName(StringUtils.lowerCase(name))));
        }
        if (text != null) {
            query = and(query, contains(Library.TEXT_ATTRIBUTE, Utils.normalizeName(StringUtils.lowerCase(text))));
        }
        if (log.isDebugEnabled()) {
            log.debug("Query {} with options {}", query, queryOptions);
        }
        return cache.retrieve(query, queryOptions);
    }


    public ResultSet<Library> selectAll(List<String> types, List<String> clans, List<String> disciplines) {
        Thresholds threshold = QueryFactory.applyThresholds(threshold(INDEX_ORDERING_SELECTIVITY, 1.0));
        QueryOptions queryOptions = queryOptions(orderBy(ascending(Library.NAME_ATTRIBUTE)), threshold);
        Query<Library> query = all(Library.class);
        if (types != null && !types.isEmpty()) {
            for (String type : types) {
                query = and(query, in(Library.TYPE_MULTI_ATTRIBUTE, type));
            }
        }
        if (clans != null && !clans.isEmpty()) {
            for (String clan : clans) {
                if (clan.equals("none")) {
                    query = and(query, equal(Library.CLAN_NUMBER_ATTRIBUTE, 0));
                } else {
                    query = and(query, in(Library.CLAN_MULTI_ATTRIBUTE, clan));
                }
            }
        }
        if (disciplines != null && !disciplines.isEmpty()) {
            for (String discipline : disciplines) {
                if (discipline.equals("none")) {
                    query = and(query, equal(Library.DISCIPLINE_NUMBER_ATTRIBUTE, 0));
                } else {
                    query = and(query, in(Library.DISCIPLINE_MULTI_ATTRIBUTE, discipline));
                }
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("Query {} with options {}", query, queryOptions);
        }
        return cache.retrieve(query, queryOptions);
    }

    public Library selectLastUpdated() {
        Thresholds threshold = QueryFactory.applyThresholds(threshold(INDEX_ORDERING_SELECTIVITY, 1.0));
        QueryOptions queryOptions = queryOptions(orderBy(descending(Library.LAST_UPDATE_ATTRIBUTE)), threshold);
        Query<Library> query = all(Library.class);
        ResultSet<Library> result = cache.retrieve(query, queryOptions);
        return (result.size() >= 1) ? result.stream().findFirst().get() : null;
    }
}
