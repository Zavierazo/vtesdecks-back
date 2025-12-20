package com.vtesdecks.controller;

import com.vtesdecks.cache.DeckIndex;
import com.vtesdecks.messaging.MessageProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/cache")
@Slf4j
@RequiredArgsConstructor
public class CacheEndpoint {
    private final CacheManager cacheManager;
    private final DeckIndex deckIndex;
    private final MessageProducer messageProducer;

    @GetMapping(value = "/clearAll", produces = {
            MediaType.TEXT_PLAIN_VALUE
    })
    public String clearAll() {
        for (String cacheName : cacheManager.getCacheNames()) {
            ConcurrentMapCache concurrentMapCache = (ConcurrentMapCache) cacheManager.getCache(cacheName);
            if (concurrentMapCache != null) {
                concurrentMapCache.invalidate();
            }
        }
        return "All caches cleared!";
    }

    @GetMapping(value = "/{cacheName}/clear", produces = {
            MediaType.TEXT_PLAIN_VALUE
    })
    public String clear(@PathVariable String cacheName) {
        ConcurrentMapCache concurrentMapCache = (ConcurrentMapCache) cacheManager.getCache(cacheName);
        if (concurrentMapCache != null) {
            concurrentMapCache.invalidate();
            return concurrentMapCache.getName() + " cleared!";
        } else {
            return "cache not found!";
        }
    }

    @GetMapping(value = "/stats", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    public Map<String, Integer> stats() {
        Map<String, Integer> stats = new HashMap<>();
        for (String cacheName : cacheManager.getCacheNames()) {
            ConcurrentMapCache concurrentMapCache = (ConcurrentMapCache) cacheManager.getCache(cacheName);
            if (concurrentMapCache != null) {
                Map<Object, Object> cache = concurrentMapCache.getNativeCache();
                stats.put(cacheName, cache.size());
            }
        }
        return stats;
    }

    @GetMapping(value = "/{cacheName}/keys", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    public List<String> keys(@PathVariable String cacheName) {
        ConcurrentMapCache concurrentMapCache = (ConcurrentMapCache) cacheManager.getCache(cacheName);
        List<String> keys = new ArrayList<>();
        if (concurrentMapCache != null) {
            Map<Object, Object> cache = concurrentMapCache.getNativeCache();
            cache.keySet().forEach(key -> keys.add(key.toString()));
            Collections.sort(keys);
        }
        return keys;
    }

    @GetMapping(value = "/{cacheName}/delete/{id}", produces = {
            MediaType.TEXT_PLAIN_VALUE
    })
    public String delete(@PathVariable String cacheName, @PathVariable String id) {
        ConcurrentMapCache concurrentMapCache = (ConcurrentMapCache) cacheManager.getCache(cacheName);
        if (concurrentMapCache != null) {
            concurrentMapCache.evict(id);
            return "DONE!";
        } else {
            return "cache not found!";
        }
    }

    @GetMapping(value = "/deck/refresh/{id}", produces = {
            MediaType.TEXT_PLAIN_VALUE
    })
    public String refreshDeck(@PathVariable String id) {
        messageProducer.publishDeckSync(id);
        return "DONE!";
    }
}
