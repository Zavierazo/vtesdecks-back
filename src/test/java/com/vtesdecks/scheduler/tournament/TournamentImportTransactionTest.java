package com.vtesdecks.scheduler.tournament;

import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.jpa.entity.DeckEntity;
import com.vtesdecks.jpa.entity.TournamentSchedulerOwner;
import com.vtesdecks.jpa.repositories.DeckRepository;
import com.vtesdecks.scheduler.tournament.helpers.TournamentImportPolicy;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TournamentImportTransactionTest {
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
        TournamentImportPolicy policy = new TournamentImportPolicy(repository,
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
                (actual, action) -> fail("A freshly verified row must not be modified"));
        jdbc.execute("SHUTDOWN");
    }
}
