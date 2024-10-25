package com.vtesdecks.cache;

import com.googlecode.cqengine.ConcurrentIndexedCollection;
import com.googlecode.cqengine.IndexedCollection;
import com.googlecode.cqengine.index.hash.HashIndex;
import com.googlecode.cqengine.query.Query;
import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.cache.indexable.proxy.ProxyCardOption;
import com.vtesdecks.db.CryptMapper;
import com.vtesdecks.db.LibraryMapper;
import com.vtesdecks.db.model.DbCrypt;
import com.vtesdecks.db.model.DbLibrary;
import com.vtesdecks.service.ProxyService;
import com.vtesdecks.util.VtesUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.googlecode.cqengine.query.QueryFactory.equal;

@Slf4j
@Component
@Order
public class ProxyCardOptionCache {
    private IndexedCollection<ProxyCardOption> cache = new ConcurrentIndexedCollection<>();

    @Autowired
    private LibraryMapper libraryMapper;

    @Autowired
    private CryptMapper cryptMapper;

    @Autowired
    private ProxyService proxyService;

    @PostConstruct
    public void setUp() {
        cache.addIndex(HashIndex.onAttribute(ProxyCardOption.CARD_ID_ATTRIBUTE));
    }

    public void refreshIndex() {
        StopWatch stopWatch = new StopWatch();
        try {
            stopWatch.start();
            fulfillCache();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            stopWatch.stop();
            log.info("Index finished in {} ms. Colletion size is {}", stopWatch.getLastTaskTimeMillis(), cache.size());
        }
    }

    public List<ProxyCardOption> get(Integer cardId) {
        Query<ProxyCardOption> findByKeyQuery = equal(ProxyCardOption.CARD_ID_ATTRIBUTE, cardId);
        ResultSet<ProxyCardOption> result = cache.retrieve(findByKeyQuery);
        return result.stream().collect(Collectors.toList());
    }

    private void fulfillCache() throws InterruptedException {
        cache.clear();
        List<ProxyCardOption> proxyOptions = getAllPossibleOptions()
                .parallel()
                .map(o -> new Object[]{o, proxyService.existsImage(o)})
                .filter(pair -> (boolean) pair[1])
                .map(pair -> (ProxyCardOption) pair[0])
                .collect(Collectors.toList());

        cache.addAll(proxyOptions);
    }

    private Stream<ProxyCardOption> getAllPossibleOptions() {
        Stream<ProxyCardOption> libraryStream = libraryMapper.selectAll().stream().flatMap(this::libraryToProxyOptions);
        Stream<ProxyCardOption> cryptStream = cryptMapper.selectAll().stream().flatMap(this::cryptToProxyOptions);

        return Stream.concat(libraryStream, cryptStream);
    }

    private Stream<ProxyCardOption> libraryToProxyOptions(DbLibrary dbLibrary) {
        return getSetsAbbrev(dbLibrary.getSet())
                .map(abbrev -> new ProxyCardOption(dbLibrary.getId(), abbrev));
    }

    private Stream<ProxyCardOption> cryptToProxyOptions(DbCrypt dbCrypt) {
        return getSetsAbbrev(dbCrypt.getSet())
                .map(abbrev -> new ProxyCardOption(dbCrypt.getId(), abbrev));
    }

    private Stream<String> getSetsAbbrev(String rawSet) {
        return VtesUtils.getSets(rawSet).stream()
                .map(s -> s.split(":")[0])
                .map(s -> s.startsWith("Promo-") ? "Promo" : s);
    }

}
