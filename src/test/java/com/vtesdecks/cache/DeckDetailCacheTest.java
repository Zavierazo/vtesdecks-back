package com.vtesdecks.cache;

import com.vtesdecks.cache.factory.DeckFactory;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.cache.indexable.DeckSummary;
import com.vtesdecks.cache.indexable.deck.SummaryStats;
import com.vtesdecks.cache.redis.repositories.DeckRedisRepository;
import com.vtesdecks.jpa.entity.DeckEntity;
import com.vtesdecks.jpa.repositories.DeckRepository;
import com.vtesdecks.jpa.repositories.LimitedFormatRepository;
import com.vtesdecks.model.DeckQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeckDetailCacheTest {
    private DeckIndex index;
    private DeckRepository database;
    private DeckRedisRepository redis;
    private DeckFactory factory;
    private DeckCardIndex cards;
    private Deck full;
    private DeckEntity entity;

    @BeforeEach
    void setUp() throws Exception {
        index = new DeckIndex();
        index.setUp();
        database = mock(DeckRepository.class);
        redis = mock(DeckRedisRepository.class);
        factory = mock(DeckFactory.class);
        cards = mock(DeckCardIndex.class);
        full = DeckCacheFixtures.deck();
        entity = new DeckEntity();
        entity.setId(full.getId());
        entity.setDeleted(false);
        ReflectionTestUtils.setField(index, "deckRepository", database);
        ReflectionTestUtils.setField(index, "deckRedisRepository", redis);
        ReflectionTestUtils.setField(index, "deckFactory", factory);
        ReflectionTestUtils.setField(index, "deckCardIndex", cards);
        ReflectionTestUtils.setField(index, "limitedFormatRepository", mock(LimitedFormatRepository.class));
        when(database.findById(full.getId())).thenReturn(Optional.of(entity));
        when(cards.refreshIndex(full.getId())).thenReturn(new DeckCardIndex.RefreshResult(List.of(), null));
        when(factory.getDeck(eq(entity), anyList(), anyList(), isNull())).thenAnswer(invocation -> full);
        var storage = new ConcurrentHashMap<String, Deck>();
        when(redis.findById(anyString())).thenAnswer(invocation -> Optional.ofNullable(storage.get(invocation.getArgument(0))));
        when(redis.save(any())).thenAnswer(invocation -> {
            Deck deck = invocation.getArgument(0);
            storage.put(deck.getId(), deck);
            return deck;
        });
        doAnswer(invocation -> storage.remove(invocation.getArgument(0))).when(redis).deleteById(anyString());
    }

    @Test
    void refreshKeepsUnopenedDecksUncachedAndListingsStayLocal() {
        index.refreshIndex(full.getId());
        assertEquals(DeckSummary.class, index.get(full.getId()).getClass());
        assertEquals(SummaryStats.class, index.get(full.getId()).getStats().getClass());
        assertEquals(full.getExtra(), index.get(full.getId()).getExtra());
        verify(redis, never()).save(any());
        clearInvocations(database, redis, factory);
        try (var result = index.selectAll(DeckQuery.builder().build())) {
            assertEquals(1, result.size());
        }
        index.get(full.getId());
        verifyNoInteractions(database, redis, factory);
    }

    @Test
    void detailsMissThenHitAndApplyConfiguredTtl() {
        index.refreshIndex(full.getId());
        clearInvocations(database, redis, factory);
        assertEquals(full, index.getFull(full.getId()));
        assertEquals(full, index.getFull(full.getId()));
        assertEquals(900L, full.getCacheTtl());
        verify(database).findById(full.getId());
        verify(redis).save(full);
        verify(redis, times(2)).findById(full.getId());
    }

    @Test
    void detailOnlyUpdateReplacesCachedDeckEvenWhenSummaryIsUnchanged() throws Exception {
        index.refreshIndex(full.getId());
        index.getFull(full.getId());
        DeckSummary previous = index.get(full.getId());
        full = DeckCacheFixtures.deck();
        full.setDescription("Changed description only");
        index.refreshIndex(full.getId());
        assertEquals(previous, index.get(full.getId()));
        clearInvocations(database, factory);
        assertEquals("Changed description only", index.getFull(full.getId()).getDescription());
        verifyNoInteractions(database, factory);
        verify(redis, never()).deleteById(anyString());
        verify(redis, times(2)).save(any());
    }

    @Test
    void hourlyRefreshReplacesExistingCacheAndPreservesRemainingTtl() throws Exception {
        ReflectionTestUtils.setField(index, "cacheTtl", Duration.ofHours(12));
        index.refreshIndex(full.getId());
        index.getFull(full.getId());
        assertEquals(43200L, full.getCacheTtl());
        full.setCacheTtl(3600L); // Only one hour remains from the original 12-hour TTL.
        full = DeckCacheFixtures.deck();
        full.setDescription("Updated during hourly refresh");
        when(database.findAll()).thenReturn(List.of(entity));

        index.refreshIndex();

        clearInvocations(database, factory);
        assertEquals("Updated during hourly refresh", index.getFull(full.getId()).getDescription());
        assertEquals(3600L, full.getCacheTtl());
        verifyNoInteractions(database, factory);
        verify(redis, never()).deleteById(anyString());
    }

    @Test
    void hourlyRefreshDoesNotPopulateExpiredOrUnopenedDecks() {
        when(database.findAll()).thenReturn(List.of(entity));
        index.refreshIndex();
        verify(redis, never()).save(any());

        index.getFull(full.getId());
        redis.deleteById(full.getId()); // Simulate expiry.
        clearInvocations(redis);
        index.refreshIndex();
        verify(redis, never()).save(any());
        assertNotNull(index.get(full.getId()));
    }

    @Test
    void refreshDoesNotResurrectEntryWhoseTtlReachedZero() {
        index.refreshIndex(full.getId());
        index.getFull(full.getId());
        full.setCacheTtl(0L);
        clearInvocations(redis);

        index.refreshIndex(full.getId());

        verify(redis, never()).save(any());
    }

    @Test
    void failedReplacementEvictsStaleEntryAndKeepsUpdatedSummary() throws Exception {
        index.refreshIndex(full.getId());
        index.getFull(full.getId());
        full = DeckCacheFixtures.deck();
        full.setName("Updated deck");
        doThrow(new IllegalStateException("Write failed")).when(redis).save(any());

        index.refreshIndex(full.getId());

        assertEquals("Updated deck", index.get(full.getId()).getName());
        assertTrue(redis.findById(full.getId()).isEmpty());
        verify(redis).deleteById(full.getId());
    }

    @Test
    void deletionRemovesBothIndexesAndCachedDetails() {
        index.refreshIndex(full.getId());
        index.getFull(full.getId());
        entity.setDeleted(true);
        index.refreshIndex(full.getId());
        assertNull(index.get(full.getId()));
        assertNull(index.getFull(full.getId()));
        verify(cards).removeDeck(full.getId());
        assertTrue(redis.findById(full.getId()).isEmpty());
    }

    @Test
    void missingDatabaseDeckOnMissRemainsNotFound() {
        index.refreshIndex(full.getId());
        when(database.findById(full.getId())).thenReturn(Optional.empty());
        assertNull(index.getFull(full.getId()));
        assertNull(index.get(full.getId()));
    }

    @Test
    void redisFailuresFallBackToDatabaseWithoutLosingTheSummary() {
        index.refreshIndex(full.getId());
        when(redis.findById(anyString())).thenThrow(new IllegalStateException("Redis unavailable"));
        doThrow(new IllegalStateException("Redis unavailable")).when(redis).save(any());
        assertEquals(full, index.getFull(full.getId()));
        assertNotNull(index.get(full.getId()));
    }

    @Test
    void concurrentMissesRebuildOnce() throws Exception {
        index.refreshIndex(full.getId());
        clearInvocations(database, factory, redis);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(8)) {
            var futures = IntStream.range(0, 8).mapToObj(i -> executor.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return index.getFull(full.getId());
            })).toList();
            start.countDown();
            for (var future : futures) {
                assertEquals(full, future.get(5, TimeUnit.SECONDS));
            }
        }
        verify(database).findById(full.getId());
        verify(redis).save(full);
    }
}
