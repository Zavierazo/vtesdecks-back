package com.vtesdecks.scheduler.tournament;

import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.jpa.entity.DeckEntity;
import com.vtesdecks.jpa.entity.TournamentSchedulerOwner;
import com.vtesdecks.jpa.repositories.DeckRepository;
import com.vtesdecks.jpa.repositories.DeckCardRepository;
import com.vtesdecks.scheduler.tournament.helpers.TournamentImportPolicy;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TournamentImportTransactionTest {
    @Test
    void veknIdMigrationPreservesExistingRowsAndAllowsUnsynchronizedPlayers() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        try {
            jdbc.execute("CREATE TABLE deck (id VARCHAR(100) PRIMARY KEY, author VARCHAR(255))");
            jdbc.update("INSERT INTO deck VALUES ('existing', 'Curated author')");

            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1.0.101__add_deck_vekn_id.sql"))
                    .execute(dataSource);

            assertNull(jdbc.queryForObject("SELECT vekn_id FROM deck WHERE id = 'existing'", String.class));
            assertEquals("Curated author", jdbc.queryForObject("SELECT author FROM deck WHERE id = 'existing'", String.class));
            jdbc.update("UPDATE deck SET vekn_id = ? WHERE id = 'existing'", "003120065");
            jdbc.update("INSERT INTO deck (id, vekn_id) VALUES ('unsynchronized-player', ?)", "0".repeat(64));
            assertEquals("003120065", jdbc.queryForObject("SELECT vekn_id FROM deck WHERE id = 'existing'", String.class));
            assertEquals("0".repeat(64), jdbc.queryForObject("SELECT vekn_id FROM deck WHERE id = 'unsynchronized-player'", String.class));
        } finally {
            jdbc.execute("SHUTDOWN");
        }
    }

    @Test
    void failedVeknIdEnrichmentRollsBackWithoutChangingVerifiedData() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        try {
            jdbc.execute("CREATE TABLE deck (id VARCHAR(100) PRIMARY KEY, vekn_id VARCHAR(64), author VARCHAR(255), verified BOOLEAN)");
            jdbc.update("INSERT INTO deck VALUES ('existing', NULL, 'Curated author', TRUE)");
            DeckRepository repository = mock(DeckRepository.class);
            when(repository.findById("existing")).thenAnswer(invocation -> Optional.of(
                    jdbc.queryForObject("SELECT * FROM deck WHERE id = 'existing'", (rs, row) ->
                            DeckEntity.builder().id(rs.getString("id")).type(DeckType.TOURNAMENT)
                                    .eventId("123").position(1).schedulerOwner(TournamentSchedulerOwner.TWDA)
                                    .veknId(rs.getString("vekn_id")).author(rs.getString("author"))
                                    .verified(rs.getBoolean("verified")).build())));
            TournamentImportPolicy policy = new TournamentImportPolicy(repository, mock(DeckCardRepository.class),
                    new TransactionTemplate(new DataSourceTransactionManager(dataSource)));

            assertThrows(IllegalStateException.class, () -> policy.execute("existing", "123", 1,
                    TournamentSchedulerOwner.ARCHON, (actual, action) -> {
                        assertEquals(TournamentImportPolicy.Action.ENRICH_METADATA, action);
                        jdbc.update("UPDATE deck SET vekn_id = '003120065' WHERE id = ?", actual.getId());
                        throw new IllegalStateException("Failure after partial persistence");
                    }));

            assertNull(jdbc.queryForObject("SELECT vekn_id FROM deck", String.class));
            assertEquals("Curated author", jdbc.queryForObject("SELECT author FROM deck", String.class));
            assertTrue(jdbc.queryForObject("SELECT verified FROM deck", Boolean.class));
        } finally {
            jdbc.execute("SHUTDOWN");
        }
    }

    @Test
    void failedTakeoverRollsBackBothOwnershipAndCards() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE deck (id VARCHAR(100) PRIMARY KEY, scheduler_owner VARCHAR(32), verified BOOLEAN)");
        jdbc.execute("CREATE TABLE deck_card (deck_id VARCHAR(100), number INT)");
        jdbc.update("INSERT INTO deck VALUES ('existing', 'ETERNAL_VIGILANCE', FALSE)");
        jdbc.update("INSERT INTO deck_card VALUES ('existing', 12)");
        DeckRepository repository = mock(DeckRepository.class);
        when(repository.findById("existing")).thenAnswer(invocation -> Optional.of(
                jdbc.queryForObject("SELECT * FROM deck WHERE id = 'existing'", (rs, row) ->
                        DeckEntity.builder().id(rs.getString("id")).type(DeckType.TOURNAMENT)
                                .schedulerOwner(TournamentSchedulerOwner.valueOf(rs.getString("scheduler_owner")))
                                .verified(rs.getBoolean("verified")).build())));
        TournamentImportPolicy policy = new TournamentImportPolicy(repository, mock(DeckCardRepository.class),
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)));

        assertThrows(IllegalStateException.class, () -> policy.execute("existing", null, 1,
                TournamentSchedulerOwner.ARCHON, (actual, action) -> {
                    jdbc.update("UPDATE deck SET scheduler_owner = 'ARCHON' WHERE id = ?", actual.getId());
                    jdbc.update("UPDATE deck_card SET number = 13 WHERE deck_id = ?", actual.getId());
                    throw new IllegalStateException("Failure after partial persistence");
                }));

        assertEquals("ETERNAL_VIGILANCE", jdbc.queryForObject("SELECT scheduler_owner FROM deck", String.class));
        assertEquals(12, jdbc.queryForObject("SELECT number FROM deck_card", Integer.class));
        assertFalse(jdbc.queryForObject("SELECT verified FROM deck", Boolean.class));

        // A later import still respects updated ownership and verification.
        jdbc.update("UPDATE deck SET scheduler_owner = 'TWDA', verified = TRUE");
        policy.execute("existing", null, 1, TournamentSchedulerOwner.ARCHON,
                (actual, action) -> assertEquals(TournamentImportPolicy.Action.ENRICH_METADATA, action));
        jdbc.execute("SHUTDOWN");
    }
}
