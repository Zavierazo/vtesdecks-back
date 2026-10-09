package com.vtesdecks.scheduler.tournament;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TournamentOwnershipMigrationTest {
    private static final String CREDIT = "Imported via <a href=\"https://github.com/gurchon-hall/channel-ten/tree/main\" target=\"_blank\" rel=\"noopener\">channel-ten</a> by Lyon Martin.";

    @Test
    void backfillsOnlyRecognizableTournamentOwnersWithoutChangingExistingData() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE deck (
                    id VARCHAR(100) PRIMARY KEY, type VARCHAR(32), source VARCHAR(512),
                    description TEXT, event_id VARCHAR(64), `position` INT, deleted BOOLEAN,
                    verified BOOLEAN, views BIGINT, creation_date TIMESTAMP,
                    modification_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("CREATE TABLE deck_card (deck_id VARCHAR(100), card_id INT, number INT)");
        Timestamp originalDate = Timestamp.valueOf(LocalDateTime.of(2020, 1, 2, 3, 4));
        Object[][] cases = {
                {"twda", "TOURNAMENT", "http://www.vekn.fr/decks/twd.htm#123", CREDIT, "TWDA"},
                {"twda-https", "TOURNAMENT", " https://www.vekn.fr/decks/twd.htm#123 ", null, "TWDA"},
                {"archon", "TOURNAMENT", "https://archon.vekn.net/tournaments/uuid", CREDIT, "ARCHON"},
                {"ev", "TOURNAMENT", "https://www.vekn.net/forum/example", "Comment<br/>" + CREDIT, "ETERNAL_VIGILANCE"},
                {"ev-no-source", "TOURNAMENT", null, CREDIT, "ETERNAL_VIGILANCE"},
                {"unknown", "TOURNAMENT", "https://example.com", null, null},
                {"not-attribution", "TOURNAMENT", null, "channel-ten", null},
                {"lookalike", "TOURNAMENT", "https://archon.vekn.net.example.com/tournaments/id", null, null},
                {"community", "COMMUNITY", "http://www.vekn.fr/decks/twd.htm#123", CREDIT, null}
        };
        for (Object[] row : cases) {
            jdbc.update("INSERT INTO deck VALUES (?, ?, ?, ?, '123', 1, FALSE, TRUE, 42, ?, ?)",
                    row[0], row[1], row[2], row[3], originalDate, originalDate);
            jdbc.update("INSERT INTO deck_card VALUES (?, 200001, 12)", row[0]);
        }
        var before = jdbc.queryForList("SELECT * FROM deck ORDER BY id");
        var cardsBefore = jdbc.queryForList("SELECT * FROM deck_card ORDER BY deck_id");

        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1.0.99__tournament_scheduler_owner.sql"))
                .execute(dataSource);

        for (Object[] row : cases) {
            assertEquals(row[4], jdbc.queryForObject("SELECT scheduler_owner FROM deck WHERE id = ?", String.class, row[0]),
                    row[0].toString());
        }
        var after = jdbc.queryForList("SELECT * FROM deck ORDER BY id");
        after.forEach(row -> row.remove("SCHEDULER_OWNER"));
        assertEquals(before, after);
        assertEquals(cardsBefore, jdbc.queryForList("SELECT * FROM deck_card ORDER BY deck_id"));
        jdbc.execute("SHUTDOWN");
    }
}
