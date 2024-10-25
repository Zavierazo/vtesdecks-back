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
import com.vtesdecks.cache.factory.CryptFactory;
import com.vtesdecks.cache.indexable.Crypt;
import com.vtesdecks.db.CardShopMapper;
import com.vtesdecks.db.CryptI18nMapper;
import com.vtesdecks.db.CryptMapper;
import com.vtesdecks.db.DeckCardMapper;
import com.vtesdecks.db.model.DbCardCount;
import com.vtesdecks.db.model.DbCardShop;
import com.vtesdecks.db.model.DbCrypt;
import com.vtesdecks.db.model.DbCryptI18n;
import com.vtesdecks.model.CryptTaint;
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
public class CryptCache {
    private IndexedCollection<Crypt> cache = new ConcurrentIndexedCollection<Crypt>();
    @Autowired
    private CryptMapper cryptMapper;
    @Autowired
    private CryptI18nMapper cryptI18nMapper;
    @Autowired
    private DeckCardMapper deckCardMapper;
    @Autowired
    private CardShopMapper cardShopMapper;
    @Autowired
    private CryptFactory cryptFactory;

    @PostConstruct
    public void setUp() {
        //Id is always unique and is the Primary Key
        cache.addIndex(UniqueIndex.onAttribute(Crypt.ID_ATTRIBUTE));
        cache.addIndex(HashIndex.onAttribute(Crypt.NAME_ATTRIBUTE));
        cache.addIndex(HashIndex.onAttribute(Crypt.TEXT_ATTRIBUTE));
        cache.addIndex(HashIndex.onAttribute(Crypt.LAST_UPDATE_ATTRIBUTE));
    }


    @Scheduled(cron = "${jobs.scrappingDecks:0 55 * * * *}")
    public void refreshIndex() {
        StopWatch stopWatch = new StopWatch();
        try {
            stopWatch.start();
            Set<Integer> currentKeys = new HashSet<>(cache.stream().map(Crypt::getId).collect(Collectors.toSet()));
            List<DbCardCount> countByCard = deckCardMapper.selectCountByCard();
            List<DbCardCount> deckCountByCard = deckCardMapper.selectDeckCountByCard();
            List<DbCardShop> cardShopList = cardShopMapper.selectAll();
            List<DbCryptI18n> cryptI18nList = cryptI18nMapper.selectAll();
            for (DbCrypt crypt : cryptMapper.selectAll()) {
                refreshIndex(crypt,
                        cryptI18nList.stream().filter(cryptI18n -> cryptI18n.getId().equals(crypt.getId())).collect(Collectors.toList()),
                        cardShopList.stream().filter(cardShop -> cardShop.getCardId().equals(crypt.getId())).collect(Collectors.toList()),
                        deckCountByCard.stream().filter(count -> count.getId().equals(crypt.getId())).mapToLong(DbCardCount::getNumber).sum(),
                        countByCard.stream().filter(count -> count.getId().equals(crypt.getId())).mapToLong(DbCardCount::getNumber).sum());
                currentKeys.remove(crypt.getId());
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

    private void refreshIndex(DbCrypt crypt, List<DbCryptI18n> cryptI18nList, List<DbCardShop> cardShopList, long deckCount, long count) {
        try {
            Crypt oldDeck = get(crypt.getId());
            Crypt newDeck = cryptFactory.getCrypt(crypt, cryptI18nList, cardShopList);
            newDeck.setDeckPopularity(deckCount);
            newDeck.setCardPopularity(count);
            if (deckCount > 0) {
                newDeck.getTaints().add(CryptTaint.TWD.getName());
            }
            if (oldDeck != null && !oldDeck.equals(newDeck)) {
                cache.update(Lists.newArrayList(oldDeck), Lists.newArrayList(newDeck));
            } else {
                cache.add(newDeck);
            }
        } catch (Exception e) {
            log.error("Error when refresh crypt {}", crypt.getId(), e);
        }
    }


    public Crypt get(Integer key) {
        Query<Crypt> findByKeyQuery = equal(Crypt.ID_ATTRIBUTE, key);
        ResultSet<Crypt> result = cache.retrieve(findByKeyQuery);
        return (result.size() >= 1) ? result.uniqueResult() : null;
    }

    public ResultSet<Crypt> selectAll(String name, String text) {
        Thresholds threshold = QueryFactory.applyThresholds(threshold(INDEX_ORDERING_SELECTIVITY, 1.0));
        QueryOptions queryOptions = queryOptions(orderBy(ascending(Crypt.NAME_ATTRIBUTE)), threshold);
        Query<Crypt> query = all(Crypt.class);
        if (name != null) {
            query = and(query, contains(Crypt.NAME_ATTRIBUTE, StringUtils.stripAccents(StringUtils.lowerCase(name))));
        }
        if (text != null) {
            query = and(query, contains(Crypt.TEXT_ATTRIBUTE, StringUtils.stripAccents(StringUtils.lowerCase(text))));
        }
        if (log.isDebugEnabled()) {
            log.debug("Query {} with options {}", query, queryOptions);
        }
        return cache.retrieve(query, queryOptions);
    }

    public Crypt selectLastUpdated() {
        Thresholds threshold = QueryFactory.applyThresholds(threshold(INDEX_ORDERING_SELECTIVITY, 1.0));
        QueryOptions queryOptions = queryOptions(orderBy(descending(Crypt.LAST_UPDATE_ATTRIBUTE)), threshold);
        Query<Crypt> query = all(Crypt.class);
        ResultSet<Crypt> result = cache.retrieve(query, queryOptions);
        return (result.size() >= 1) ? result.stream().findFirst().get() : null;
    }
}
