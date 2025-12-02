package com.vtesdecks.configuration;

import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.DeckIndex;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.SetCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class BeforeStartUpActions implements InitializingBean {
    private final DeckIndex deckIndex;
    private final CryptCache cryptCache;
    private final LibraryCache libraryCache;
    private final SetCache setCache;

    @Override
    public void afterPropertiesSet() throws Exception {
        beforeStartUpActions();
    }

    private void beforeStartUpActions() throws Exception {
        // Start async tasks thread
        ExecutorService executor = null;
        try {
            log.info("Starting concurrent indexing...");
            executor = Executors.newFixedThreadPool(3);
            executor.execute(() -> cryptCache.refreshIndex());
            executor.execute(() -> libraryCache.refreshIndex());
            executor.execute(() -> setCache.refreshIndex());
        } finally {
            if (executor != null) {
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(5, TimeUnit.MINUTES)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            log.info("Finish concurrent indexing...");
        }
        log.info("Starting serial indexing...");
        deckIndex.refreshIndex();
        log.info("Finish serial indexing...");
    }
}
