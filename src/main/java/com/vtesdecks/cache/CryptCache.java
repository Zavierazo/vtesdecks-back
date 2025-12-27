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
import com.vtesdecks.jpa.entity.CardShopEntity;
import com.vtesdecks.jpa.entity.CryptEntity;
import com.vtesdecks.jpa.entity.CryptI18nEntity;
import com.vtesdecks.jpa.entity.extra.DeckCardCount;
import com.vtesdecks.jpa.repositories.CardShopRepository;
import com.vtesdecks.jpa.repositories.CryptI18nRepository;
import com.vtesdecks.jpa.repositories.CryptRepository;
import com.vtesdecks.jpa.repositories.DeckCardRepository;
import com.vtesdecks.model.CryptTaint;
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
public class CryptCache {
    private IndexedCollection<Crypt> cache = new ConcurrentIndexedCollection<Crypt>();
    @Autowired
    private CryptRepository cryptRepository;
    @Autowired
    private CryptI18nRepository cryptI18nRepository;
    @Autowired
    private DeckCardRepository deckCardRepository;
    @Autowired
    private CardShopRepository cardShopRepository;
    @Autowired
    private CryptFactory cryptFactory;

    @PostConstruct
    public void setUp() {
        //Id is always unique and is the Primary Key
        cache.addIndex(UniqueIndex.onAttribute(Crypt.ID_ATTRIBUTE));
        cache.addIndex(HashIndex.onAttribute(Crypt.NAME_ATTRIBUTE));
        cache.addIndex(HashIndex.onAttribute(Crypt.TEXT_ATTRIBUTE));
        cache.addIndex(HashIndex.onAttribute(Crypt.TYPE_ATTRIBUTE));
        cache.addIndex(HashIndex.onAttribute(Crypt.CLAN_ATTRIBUTE));
        cache.addIndex(HashIndex.onAttribute(Crypt.DISCIPLINE_NUMBER_ATTRIBUTE));
        cache.addIndex(HashIndex.onAttribute(Crypt.DISCIPLINE_MULTI_ATTRIBUTE));
        cache.addIndex(HashIndex.onAttribute(Crypt.LAST_UPDATE_ATTRIBUTE));
    }


    @Scheduled(cron = "${jobs.cache.refresh:0 55 * * * *}")
    public void refreshIndex() {
        StopWatch stopWatch = new StopWatch();
        try {
            stopWatch.start();
            Set<Integer> currentKeys = cache.stream().map(Crypt::getId).collect(Collectors.toSet());
            List<DeckCardCount> countByCard = deckCardRepository.selectCountByCard();
            List<DeckCardCount> deckCountByCard = deckCardRepository.selectDeckCountByCard();
            List<CardShopEntity> cardShopList = cardShopRepository.findAll();
            List<CryptI18nEntity> cryptI18nList = cryptI18nRepository.findAll();
            for (CryptEntity crypt : cryptRepository.findAll()) {
                refreshIndex(crypt,
                        cryptI18nList.stream().filter(cryptI18n -> cryptI18n.getId().getCardId().equals(crypt.getId())).toList(),
                        cardShopList.stream().filter(cardShop -> cardShop.getCardId().equals(crypt.getId())).toList(),
                        deckCountByCard.stream().filter(count -> count.getId().equals(crypt.getId())).mapToLong(DeckCardCount::getNumberAsLong).sum(),
                        countByCard.stream().filter(count -> count.getId().equals(crypt.getId())).mapToLong(DeckCardCount::getNumberAsLong).sum());
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
            log.info("Index finished in {} ms. Colletion size is {}", stopWatch.lastTaskInfo().getTimeMillis(), cache.size());
        }
    }

    private void refreshIndex(CryptEntity crypt, List<CryptI18nEntity> cryptI18nList, List<CardShopEntity> cardShopList, long deckCount, long count) {
        try {
            Crypt oldCrypt = get(crypt.getId());
            Crypt newCrypt = cryptFactory.getCrypt(crypt, cryptI18nList, cardShopList);
            newCrypt.setDeckPopularity(deckCount);
            newCrypt.setCardPopularity(count);
            if (deckCount > 0) {
                newCrypt.getTaints().add(CryptTaint.TWD.getName());
            }
            if (oldCrypt != null && !oldCrypt.equals(newCrypt)) {
                cache.update(Lists.newArrayList(oldCrypt), Lists.newArrayList(newCrypt));
            } else if (oldCrypt == null) {
                cache.add(newCrypt);
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

    public ResultSet<Crypt> selectByExactName(String name) {
        Thresholds threshold = QueryFactory.applyThresholds(threshold(INDEX_ORDERING_SELECTIVITY, 1.0));
        QueryOptions queryOptions = queryOptions(orderBy(ascending(Crypt.NAME_ATTRIBUTE)), threshold);
        Query<Crypt> query = equal(Crypt.NAME_ATTRIBUTE, Utils.normalizeName(StringUtils.lowerCase(name)));

        if (log.isDebugEnabled()) {
            log.debug("Query {} with options {}", query, queryOptions);
        }
        return cache.retrieve(query, queryOptions);
    }

    public ResultSet<Crypt> selectAll(String name, String text) {
        Thresholds threshold = QueryFactory.applyThresholds(threshold(INDEX_ORDERING_SELECTIVITY, 1.0));
        QueryOptions queryOptions = queryOptions(orderBy(ascending(Crypt.NAME_ATTRIBUTE)), threshold);
        Query<Crypt> query = all(Crypt.class);
        if (name != null) {
            query = and(query, contains(Crypt.NAME_ATTRIBUTE, Utils.normalizeName(StringUtils.lowerCase(name))));
        }
        if (text != null) {
            query = and(query, contains(Crypt.TEXT_ATTRIBUTE, Utils.normalizeName(StringUtils.lowerCase(text))));
        }
        if (log.isDebugEnabled()) {
            log.debug("Query {} with options {}", query, queryOptions);
        }
        return cache.retrieve(query, queryOptions);
    }

    public ResultSet<Crypt> selectAll(List<String> types, List<String> clans, List<String> disciplines) {
        Thresholds threshold = QueryFactory.applyThresholds(threshold(INDEX_ORDERING_SELECTIVITY, 1.0));
        QueryOptions queryOptions = queryOptions(orderBy(ascending(Crypt.NAME_ATTRIBUTE)), threshold);
        Query<Crypt> query = all(Crypt.class);
        if (types != null && !types.isEmpty()) {
            for (String type : types) {
                query = and(query, equal(Crypt.TYPE_ATTRIBUTE, type));
            }
        }
        if (clans != null && !clans.isEmpty()) {
            for (String clan : clans) {
                if (clan.equals("none")) {
                    query = and(query, equal(Crypt.CLAN_ATTRIBUTE, ""));
                } else {
                    query = and(query, equal(Crypt.CLAN_ATTRIBUTE, clan));
                }
            }
        }
        if (disciplines != null && !disciplines.isEmpty()) {
            for (String discipline : disciplines) {
                if (discipline.equals("none")) {
                    query = and(query, equal(Crypt.DISCIPLINE_NUMBER_ATTRIBUTE, 0));
                } else {
                    query = and(query, in(Crypt.DISCIPLINE_MULTI_ATTRIBUTE, discipline));
                }
            }
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
