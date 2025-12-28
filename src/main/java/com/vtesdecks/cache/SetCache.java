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
import com.vtesdecks.cache.factory.SetFactory;
import com.vtesdecks.cache.indexable.Set;
import com.vtesdecks.jpa.entity.SetEntity;
import com.vtesdecks.jpa.repositories.SetRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.util.Map;
import java.util.stream.Collectors;

import static com.googlecode.cqengine.query.QueryFactory.all;
import static com.googlecode.cqengine.query.QueryFactory.ascending;
import static com.googlecode.cqengine.query.QueryFactory.descending;
import static com.googlecode.cqengine.query.QueryFactory.equal;
import static com.googlecode.cqengine.query.QueryFactory.orderBy;
import static com.googlecode.cqengine.query.QueryFactory.queryOptions;
import static com.googlecode.cqengine.query.QueryFactory.threshold;
import static com.googlecode.cqengine.query.option.EngineThresholds.INDEX_ORDERING_SELECTIVITY;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SetCache {
    private IndexedCollection<Set> cache = new ConcurrentIndexedCollection<>();

    @Autowired
    private SetRepository setRepository;
    @Autowired
    private SetFactory setFactory;

    @PostConstruct
    public void setUp() {
        cache.addIndex(UniqueIndex.onAttribute(Set.ID_ATTRIBUTE));
        cache.addIndex(UniqueIndex.onAttribute(Set.ABBREV_ATTRIBUTE));
        cache.addIndex(HashIndex.onAttribute(Set.FULL_NAME_ATTRIBUTE));
        cache.addIndex(HashIndex.onAttribute(Set.RELEASE_ATTRIBUTE));
        cache.addIndex(HashIndex.onAttribute(Set.LAST_UPDATE_ATTRIBUTE));
    }

    @Scheduled(cron = "${jobs.cache.set.refresh:0 45 * * * *}")
    public void refreshIndex() {
        StopWatch stopWatch = new StopWatch();
        try {
            stopWatch.start();
            java.util.Set<Set> currentKeys = cache.stream().collect(Collectors.toSet());
            for (SetEntity set : setRepository.findAll()) {
                refreshIndex(set);
                currentKeys.removeIf(s -> s.getId().equals(set.getId()));
            }
            if (!currentKeys.isEmpty()) {
                log.warn("Deleting form index {}", currentKeys);
                for (Set set : currentKeys) {
                    cache.remove(set);
                }
            }
        } finally {
            stopWatch.stop();
            log.info("Index finished in {} ms. Colletion size is {}", stopWatch.lastTaskInfo().getTimeMillis(), cache.size());
        }
    }

    public void refreshIndex(SetEntity set) {
        try {
            Set oldSet = get(set.getId());
            Set newSet = setFactory.getSet(set);
            if (oldSet != null && newSet == null) {
                cache.remove(oldSet);
            } else if (oldSet != null && !oldSet.equals(newSet)) {
                cache.update(Lists.newArrayList(oldSet), Lists.newArrayList(newSet));
            } else if (oldSet == null && newSet != null) {
                cache.add(newSet);
            }
        } catch (Exception e) {
            log.error("Error when refresh library {}", set.getId(), e);
        }
    }

    public void deleteIndex(Integer key) {
        Set set = get(key);
        if (set != null) {
            cache.remove(set);
        }
    }

    public Set get(Integer key) {
        Query<Set> findByKeyQuery = equal(Set.ID_ATTRIBUTE, key);
        ResultSet<Set> result = cache.retrieve(findByKeyQuery);
        return (result.size() >= 1) ? result.uniqueResult() : null;
    }

    public Set get(String key) {
        Query<Set> findByKeyQuery = equal(Set.ABBREV_ATTRIBUTE, key);
        ResultSet<Set> result = cache.retrieve(findByKeyQuery);
        return (result.size() >= 1) ? result.uniqueResult() : null;
    }

    public Set getByFullName(String key) {
        Query<Set> findByKeyQuery = equal(Set.FULL_NAME_ATTRIBUTE, key);
        ResultSet<Set> result = cache.retrieve(findByKeyQuery);
        return (result.size() >= 1) ? result.uniqueResult() : null;
    }

    public Map<String, Map<String, Integer>> select() {
        return null;
    }

    public ResultSet<Set> selectAll() {
        Query<Set> query = all(Set.class);
        QueryOptions queryOptions = queryOptions(orderBy(ascending(Set.RELEASE_ATTRIBUTE)));

        return cache.retrieve(query, queryOptions);
    }

    public Set selectLastUpdated() {
        Thresholds threshold = QueryFactory.applyThresholds(threshold(INDEX_ORDERING_SELECTIVITY, 1.0));
        QueryOptions queryOptions = queryOptions(orderBy(descending(Set.LAST_UPDATE_ATTRIBUTE)), threshold);
        Query<Set> query = all(Set.class);
        ResultSet<Set> result = cache.retrieve(query, queryOptions);
        return (result.size() >= 1) ? result.stream().findFirst().get() : null;
    }
}
