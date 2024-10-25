package com.vtesdecks.cache;

import com.google.common.collect.Lists;
import com.googlecode.cqengine.ConcurrentIndexedCollection;
import com.googlecode.cqengine.IndexedCollection;
import com.googlecode.cqengine.index.unique.UniqueIndex;
import com.googlecode.cqengine.query.Query;
import com.googlecode.cqengine.query.option.QueryOptions;
import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.cache.factory.SetFactory;
import com.vtesdecks.cache.indexable.Set;
import com.vtesdecks.db.SetMapper;
import com.vtesdecks.db.model.DbSet;
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
import static com.googlecode.cqengine.query.QueryFactory.equal;
import static com.googlecode.cqengine.query.QueryFactory.orderBy;
import static com.googlecode.cqengine.query.QueryFactory.queryOptions;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SetCache {
    private IndexedCollection<Set> cache = new ConcurrentIndexedCollection<>();

    @Autowired
    private SetMapper setMapper;
    @Autowired
    private SetFactory setFactory;

    @PostConstruct
    public void setUp() {
        cache.addIndex(UniqueIndex.onAttribute(Set.ID_ATTRIBUTE));
        cache.addIndex(UniqueIndex.onAttribute(Set.ABBREV_ATTRIBUTE));
    }

    @Scheduled(cron = "${jobs.scrappingDecks:0 55 * * * *}")
    public void refreshIndex() {
        StopWatch stopWatch = new StopWatch();
        try {
            stopWatch.start();
            java.util.Set<Integer> currentKeys = cache.stream().map(Set::getId).collect(Collectors.toSet());
            for (DbSet set : setMapper.selectAll()) {
                refreshIndex(set);
                currentKeys.remove(set.getId());
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

    public void refreshIndex(DbSet set) {
        try {
            Set oldDeck = get(set.getId());
            Set newDeck = setFactory.getSet(set);
            if (oldDeck != null && !oldDeck.equals(newDeck)) {
                cache.update(Lists.newArrayList(oldDeck), Lists.newArrayList(newDeck));
            } else {
                cache.add(newDeck);
            }
        } catch (Exception e) {
            log.error("Error when refresh library {}", set.getId(), e);
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

    public Map<String, Map<String, Integer>> select() {
        return null;
    }

    public ResultSet<Set> selectAll() {
        Query<Set> query = all(Set.class);
        QueryOptions queryOptions = queryOptions(orderBy(ascending(Set.RELEASE_ATTRIBUTE)));

        return cache.retrieve(query, queryOptions);
    }
}
