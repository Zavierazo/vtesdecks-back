package com.vtesdecks.service;

import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.DeckIndex;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.SetCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncAfterStartupService {
    private final AfterStartupService afterStartupService;
    private final SetCache setCache;
    private final CryptCache cryptCache;
    private final LibraryCache libraryCache;
    private final DeckIndex deckIndex;

    @Async
    public void doAsyncAfterStartupTasks() {
        // The proxied importer commits before cache workers read the new cards.
        if (afterStartupService.executeAfterStartupTasks()) {
            setCache.refreshIndex();
            cryptCache.refreshIndex();
            libraryCache.refreshIndex();
            deckIndex.refreshIndex();
        }
        log.info("Finish background tasks...");
    }
}
