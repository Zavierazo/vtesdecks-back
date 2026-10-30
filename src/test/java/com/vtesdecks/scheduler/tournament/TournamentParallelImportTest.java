package com.vtesdecks.scheduler.tournament;

import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.jpa.entity.DeckCardEntity;
import com.vtesdecks.jpa.entity.DeckEntity;
import com.vtesdecks.jpa.entity.TournamentSchedulerOwner;
import com.vtesdecks.jpa.repositories.DeckCardRepository;
import com.vtesdecks.jpa.repositories.DeckRepository;
import com.vtesdecks.scheduler.tournament.helpers.TournamentImportExecutor;
import com.vtesdecks.scheduler.tournament.helpers.TournamentImportPolicy;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.jpa.repository.Query;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TournamentParallelImportTest {
    @Test
    void releasesImportLocksAfterATransactionFails() throws Exception {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        TournamentImportPolicy policy = new TournamentImportPolicy(mock(DeckRepository.class), mock(DeckCardRepository.class), new TransactionTemplate(manager));
        LocalDate day = LocalDate.of(2026, 10, 3);
        assertThrows(IllegalStateException.class, () -> policy.execute("failed-deck", "failed-event", 1,
                TournamentSchedulerOwner.ARCHON, day, (actual, action) -> {
                    throw new IllegalStateException("Import failed");
                }));
        CompletableFuture<Void> retry = new CompletableFuture<>();
        Thread.ofVirtual().start(() -> {
            try {
                policy.execute("failed-deck", "failed-event", 1, TournamentSchedulerOwner.ARCHON, day, (actual, action) -> { });
                retry.complete(null);
            } catch (Throwable error) {
                retry.completeExceptionally(error);
            }
        });
        retry.get(10, TimeUnit.SECONDS);
        verify(manager).rollback(any());
        verify(manager).commit(any());
    }

    @Test
    void parallelDuplicateChecksSeeCommittedCardsAndStillAllowConsecutiveTournaments() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        NamedParameterJdbcTemplate namedJdbc = new NamedParameterJdbcTemplate(dataSource);
        DeckRepository decks = mock(DeckRepository.class);
        DeckCardRepository cards = mock(DeckCardRepository.class);
        TournamentImportPolicy policy = new TournamentImportPolicy(decks, cards,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
        try {
            jdbc.execute("CREATE TABLE deck (id VARCHAR(100) PRIMARY KEY, type VARCHAR(32), deleted BOOLEAN,"
                    + " author VARCHAR(255), tournament VARCHAR(255), event_id VARCHAR(100), creation_date TIMESTAMP)");
            jdbc.execute("CREATE TABLE deck_card (deck_id VARCHAR(100), id INT, number INT, PRIMARY KEY(deck_id, id))");
            String query = DeckRepository.class.getMethod("findActiveTournamentDecksBetween", LocalDateTime.class, LocalDateTime.class)
                    .getAnnotation(Query.class).value();
            when(decks.findActiveTournamentDecksBetween(any(), any())).thenAnswer(invocation -> namedJdbc.query(query,
                    Map.of("start", invocation.getArgument(0), "end", invocation.getArgument(1)),
                    (row, index) -> DeckEntity.builder().id(row.getString("id")).type(DeckType.TOURNAMENT).position(1)
                            .eventId(row.getString("event_id")).tournament(row.getString("tournament")).author(row.getString("author"))
                            .creationDate(row.getTimestamp("creation_date").toLocalDateTime()).build()));
            when(cards.findByIdDeckIdIn(any())).thenAnswer(invocation -> namedJdbc.query(
                    "SELECT deck_id, id, number FROM deck_card WHERE deck_id IN (:ids)",
                    Map.of("ids", invocation.getArgument(0)), (row, index) -> card(row.getString("deck_id"), row.getInt("id"), row.getInt("number"))));
            AtomicInteger duplicates = new AtomicInteger();

            TournamentImportExecutor.forEach(IntStream.range(0, 80).boxed().toList(), 4, index -> {
                String id = "deck-" + index;
                String eventId = "event-" + index;
                LocalDate day = LocalDate.of(2026, 10, 3).plusDays(index % 2);
                policy.execute(id, eventId, 1, TournamentSchedulerOwner.ARCHON, day, (actual, action) -> {
                    DeckEntity incoming = DeckEntity.builder().id(id).type(DeckType.TOURNAMENT).position(1).eventId(eventId)
                            .tournament("Different label " + index).author("Player").creationDate(day.atStartOfDay()).build();
                    if (!policy.findDuplicates(actual, incoming, List.of(card(id, 200001, 12), card(id, 100001, 60))).isEmpty()) {
                        duplicates.incrementAndGet();
                        return;
                    }
                    jdbc.update("INSERT INTO deck VALUES (?, 'TOURNAMENT', FALSE, 'Player', ?, ?, ?)", id, incoming.getTournament(), eventId, day.atStartOfDay());
                    jdbc.update("INSERT INTO deck_card VALUES (?, 200001, 12), (?, 100001, 60)", id, id);
                });
            });

            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM deck", Integer.class));
            assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM deck_card", Integer.class));
            assertEquals(78, duplicates.get());
        } finally {
            jdbc.execute("SHUTDOWN");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"date", "event", "id"})
    void coordinatesConflictingImportsBeforeOpeningTheirTransactions(String sharedKey) throws Exception {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        TournamentImportPolicy policy = new TournamentImportPolicy(mock(DeckRepository.class), mock(DeckCardRepository.class), new TransactionTemplate(manager));
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondAttempted = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);
        LocalDate firstDate = LocalDate.of(2026, 10, 3);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> policy.execute("first", "first-event", 1, TournamentSchedulerOwner.ARCHON, firstDate, (actual, action) -> {
                firstEntered.countDown();
                try {
                    assertTrue(releaseFirst.await(10, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }));
            try {
                assertTrue(firstEntered.await(10, TimeUnit.SECONDS));
                var second = workers.submit(() -> {
                    secondAttempted.countDown();
                    policy.execute(sharedKey.equals("id") ? "first" : "second", sharedKey.equals("event") ? "first-event" : "second-event",
                            1, TournamentSchedulerOwner.ARCHON, sharedKey.equals("date") ? firstDate : firstDate.plusDays(1),
                            (actual, action) -> secondEntered.countDown());
                });
                assertTrue(secondAttempted.await(10, TimeUnit.SECONDS));
                assertFalse(secondEntered.await(200, TimeUnit.MILLISECONDS));
                verify(manager, times(1)).getTransaction(any());
                releaseFirst.countDown();
                second.get(10, TimeUnit.SECONDS);
                first.get(10, TimeUnit.SECONDS);
                assertEquals(0, secondEntered.getCount());
                verify(manager, times(2)).commit(any());
            } finally {
                releaseFirst.countDown();
            }
        }
    }

    private static DeckCardEntity card(String deckId, int cardId, int quantity) {
        DeckCardEntity card = new DeckCardEntity();
        card.setId(new DeckCardEntity.DeckCardId());
        card.getId().setDeckId(deckId);
        card.getId().setCardId(cardId);
        card.setNumber(quantity);
        return card;
    }
}
