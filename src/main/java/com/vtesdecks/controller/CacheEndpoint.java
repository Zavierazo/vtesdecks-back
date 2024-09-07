package com.vtesdecks.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.github.benmanes.caffeine.cache.Cache;
import com.vtesdecks.cache.DeckIndex;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/cache")
@Slf4j
public class CacheEndpoint {
    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private DeckIndex deckIndex;

    @RequestMapping(method = RequestMethod.GET, value = "/clearAll", produces = {
        MediaType.TEXT_PLAIN_VALUE
    })
    @ResponseBody
    public String clearAll() throws Exception {
        for (String cacheName : cacheManager.getCacheNames()) {
            CaffeineCache caffeineCache = (CaffeineCache) cacheManager.getCache(cacheName);
            caffeineCache.invalidate();
        }
        return "All caches cleared!";
    }

    @RequestMapping(method = RequestMethod.GET, value = "/{cacheName}/clear", produces = {
        MediaType.TEXT_PLAIN_VALUE
    })
    @ResponseBody
    public String clear(@PathVariable String cacheName) throws Exception {
        CaffeineCache caffeineCache = (CaffeineCache) cacheManager.getCache(cacheName);
        caffeineCache.invalidate();
        return "Cache " + cacheName + " cleared!";
    }

    @RequestMapping(method = RequestMethod.GET, value = "/stats", produces = {
        MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public Map<String, Integer> stats() throws Exception {
        Map<String, Integer> stats = new HashMap<>();
        for (String cacheName : cacheManager.getCacheNames()) {
            CaffeineCache caffeineCache = (CaffeineCache) cacheManager.getCache(cacheName);
            Cache<Object, Object> cache = caffeineCache.getNativeCache();
            stats.put(cacheName, cache.asMap().size());
        }
        return stats;
    }

    @RequestMapping(method = RequestMethod.GET, value = "/{cacheName}/keys", produces = {
        MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public List<String> keys(@PathVariable String cacheName) throws Exception {
        CaffeineCache caffeineCache = (CaffeineCache) cacheManager.getCache(cacheName);
        Cache<Object, Object> cache = caffeineCache.getNativeCache();
        List<String> keys = new ArrayList<>();
        cache.asMap().keySet().stream().forEach(key -> keys.add(key.toString()));
        Collections.sort(keys);
        return keys;
    }

    @RequestMapping(method = RequestMethod.GET, value = "/{cacheName}/delete/{id}", produces = {
        MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public String delete(@PathVariable String cacheName, @PathVariable String id) throws Exception {
        CaffeineCache caffeineCache = (CaffeineCache) cacheManager.getCache(cacheName);
        Cache<Object, Object> cache = caffeineCache.getNativeCache();
        cache.invalidate(id);
        return "DONE!";
    }

    @RequestMapping(method = RequestMethod.GET, value = "/deck/refresh/{id}", produces = {
        MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public String refreshDeck(@PathVariable String id) throws Exception {
        deckIndex.enqueueRefreshIndex(id);
        return "DONE!";
    }
}
