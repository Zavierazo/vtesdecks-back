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
import com.vtesdecks.db.CardShopMapper;
import com.vtesdecks.db.DeckCardMapper;
import com.vtesdecks.db.LibraryI18nMapper;
import com.vtesdecks.db.LibraryMapper;
import com.vtesdecks.db.model.DbCardCount;
import com.vtesdecks.db.model.DbCardShop;
import com.vtesdecks.db.model.DbLibrary;
import com.vtesdecks.db.model.DbLibraryI18n;
import com.vtesdecks.model.LibraryTaint;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.googlecode.cqengine.query.QueryFactory.all;
import static com.googlecode.cqengine.query.QueryFactory.and;
import static com.googlecode.cqengine.query.QueryFactory.ascending;
import static com.googlecode.cqengine.query.QueryFactory.contains;
import static com.googlecode.cqengine.query.QueryFactory.descending;
import static com.googlecode.cqengine.query.QueryFactory.equal;
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
    private LibraryMapper libraryMapper;
    @Autowired
    private LibraryI18nMapper libraryI18nMapper;
    @Autowired
    private DeckCardMapper deckCardMapper;
    @Autowired
    private CardShopMapper cardShopMapper;
    @Autowired
    private LibraryFactory libraryFactory;


    @PostConstruct
    public void setUp() {
        //Id is always unique and is the Primary Key
        cache.addIndex(UniqueIndex.onAttribute(Library.ID_ATTRIBUTE));
        cache.addIndex(HashIndex.onAttribute(Library.NAME_ATTRIBUTE));
        cache.addIndex(HashIndex.onAttribute(Library.TEXT_ATTRIBUTE));
        cache.addIndex(HashIndex.onAttribute(Library.LAST_UPDATE_ATTRIBUTE));
    }


    @Scheduled(cron = "${jobs.scrappingDecks:0 55 * * * *}")
    public void refreshIndex() {
        StopWatch stopWatch = new StopWatch();
        try {
            stopWatch.start();
            Set<Integer> currentKeys = new HashSet<>(cache.stream().map(Library::getId).collect(Collectors.toSet()));
            List<DbCardCount> countByCard = deckCardMapper.selectCountByCard();
            List<DbCardCount> deckCountByCard = deckCardMapper.selectDeckCountByCard();
            List<DbCardShop> cardShopList = cardShopMapper.selectAll();
            List<DbLibraryI18n> libraryI18nList = libraryI18nMapper.selectAll();
            for (DbLibrary library : libraryMapper.selectAll()) {
                refreshIndex(library,
                        libraryI18nList.stream().filter(libraryI18n -> libraryI18n.getId().equals(library.getId())).collect(Collectors.toList()),
                        cardShopList.stream().filter(cardShop -> cardShop.getCardId().equals(library.getId())).collect(Collectors.toList()),
                        deckCountByCard.stream().filter(count -> count.getId().equals(library.getId())).mapToLong(DbCardCount::getNumber).sum(),
                        countByCard.stream().filter(count -> count.getId().equals(library.getId())).mapToLong(DbCardCount::getNumber).sum());
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
            log.info("Index finished in {} ms. Colletion size is {}", stopWatch.getLastTaskTimeMillis(), cache.size());
        }
    }

    private void refreshIndex(DbLibrary library, List<DbLibraryI18n> libraryI18nList, List<DbCardShop> cardShopList, Long deckCount, Long count) {
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
            } else {
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


    public ResultSet<Library> selectAll(String name, String text) {
        Thresholds threshold = QueryFactory.applyThresholds(threshold(INDEX_ORDERING_SELECTIVITY, 1.0));
        QueryOptions queryOptions = queryOptions(orderBy(ascending(Library.NAME_ATTRIBUTE)), threshold);
        Query<Library> query = all(Library.class);
        if (name != null) {
            query = and(query, contains(Library.NAME_ATTRIBUTE, StringUtils.stripAccents(StringUtils.lowerCase(name))));
        }
        if (text != null) {
            query = and(query, contains(Library.TEXT_ATTRIBUTE, StringUtils.stripAccents(StringUtils.lowerCase(text))));
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
