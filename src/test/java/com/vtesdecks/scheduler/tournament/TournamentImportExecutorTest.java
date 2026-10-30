package com.vtesdecks.scheduler.tournament;

import com.vtesdecks.scheduler.tournament.helpers.TournamentImportExecutor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class TournamentImportExecutorTest {
    @Test
    void limitsConcurrencyProcessesEachItemOnceAndWaitsForCompletion() throws Exception {
        CountDownLatch entered = new CountDownLatch(4);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        Set<Integer> processed = ConcurrentHashMap.newKeySet();
        List<Integer> items = IntStream.range(0, 40).boxed().toList();
        try (var caller = Executors.newSingleThreadExecutor()) {
            var result = caller.submit(() -> assertDoesNotThrow(() ->
                    TournamentImportExecutor.forEach(items, 4, item -> {
                        peak.accumulateAndGet(active.incrementAndGet(), Math::max);
                        entered.countDown();
                        try {
                            assertTrue(release.await(10, TimeUnit.SECONDS));
                            assertTrue(processed.add(item));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(e);
                        } finally {
                            active.decrementAndGet();
                        }
                    })));
            try {
                assertTrue(entered.await(10, TimeUnit.SECONDS));
                assertFalse(result.isDone());
            } finally {
                release.countDown();
            }
            result.get(10, TimeUnit.SECONDS);
        }
        assertEquals(4, peak.get());
        assertEquals(Set.copyOf(items), processed);
        assertEquals(0, active.get());
    }

    @Test
    void supportsSequentialProcessingAndEmptyInput() throws Exception {
        Thread caller = Thread.currentThread();
        List<Integer> processed = new java.util.ArrayList<>();
        TournamentImportExecutor.forEach(List.of(1, 2, 3), 1, item -> {
            assertSame(caller, Thread.currentThread());
            processed.add(item);
        });
        assertEquals(List.of(1, 2, 3), processed);
        TournamentImportExecutor.forEach(List.of(), 4, item -> fail("Empty input must not invoke the importer"));
    }

    @Test
    void propagatesUnexpectedWorkerFailuresAfterClosingThePool() {
        ExecutionException error = assertThrows(ExecutionException.class, () ->
                TournamentImportExecutor.forEach(List.of(1, 2, 3, 4), 2, item -> {
                    if (item == 1) {
                        throw new IllegalStateException("Unexpected import failure");
                    }
                }));
        assertInstanceOf(IllegalStateException.class, error.getCause());
    }
}
