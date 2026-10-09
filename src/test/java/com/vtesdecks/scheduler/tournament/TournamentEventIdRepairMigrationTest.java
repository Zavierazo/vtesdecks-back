package com.vtesdecks.scheduler.tournament;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TournamentEventIdRepairMigrationTest {
    @Test
    void repairsMigrationArtifactsWithoutChangingValidCodesOrOtherData() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE deck (
                    id VARCHAR(100) PRIMARY KEY, type VARCHAR(32), url VARCHAR(512), event_id VARCHAR(64),
                    scheduler_owner VARCHAR(32), `position` INT, final_vp DECIMAL(3,1), final_seat INT,
                    verified BOOLEAN, deleted BOOLEAN, creation_date TIMESTAMP,
                    modification_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("CREATE TABLE deck_card (deck_id VARCHAR(100), card_id INT, number INT)");
        Object[][] cases = {
                {"tournament-12816", "TOURNAMENT", "https://www.vekn.net/event-calendar/event/12816", "2", "12816"},
                {"no-www", "TOURNAMENT", "http://vekn.net/event-calendar/event/12345/", "2", "12345"},
                {"archon-short", "TOURNAMENT", "https://archon.vekn.net/t/abc-123/", "1", "abc-123"},
                {"archon-long", "TOURNAMENT", "http://archon.vekn.net/tournament/def-456/display.html", "1", "def-456"},
                {"archon-long-slash", "TOURNAMENT", "https://archon.vekn.net/tournament/def-456/display.html/", "1", "def-456"},
                {"valid", "TOURNAMENT", "https://www.vekn.net/event-calendar/event/12816", "12816", "12816"},
                {"event-code", "TOURNAMENT", "https://www.vekn.net/event-calendar/event/12816", "ABC123", "ABC123"},
                {"modern-archon", "TOURNAMENT", "https://archon.vekn.net/tournaments/uuid", "1", "1"},
                {"unrelated", "TOURNAMENT", "https://example.com/12816", "2", "2"},
                {"lookalike", "TOURNAMENT", "https://www.vekn.net.example.com/event-calendar/event/12816", "2", "2"},
                {"missing-url", "TOURNAMENT", null, "2", "2"},
                {"missing-id", "TOURNAMENT", "https://www.vekn.net/event-calendar/event/12816", null, null},
                {"community", "COMMUNITY", "https://www.vekn.net/event-calendar/event/12816", "2", "2"},
                {"real-event-two", "TOURNAMENT", "https://www.vekn.net/event-calendar/event/2", "2", "2"}
        };
        Timestamp date = Timestamp.valueOf(LocalDateTime.of(2020, 1, 2, 3, 4));
        for (Object[] row : cases) {
            jdbc.update("INSERT INTO deck VALUES (?, ?, ?, ?, 'ETERNAL_VIGILANCE', 1, 2.5, 4, TRUE, FALSE, ?, ?)",
                    row[0], row[1], row[2], row[3], date, date);
            jdbc.update("INSERT INTO deck_card VALUES (?, 200001, 12)", row[0]);
        }
        var before = jdbc.queryForList("SELECT * FROM deck ORDER BY id");
        before.forEach(row -> row.remove("EVENT_ID"));
        var cardsBefore = jdbc.queryForList("SELECT * FROM deck_card ORDER BY deck_id");
        var migration = new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V1.0.100__repair_tournament_event_ids.sql"));
        migration.execute(dataSource);
        for (Object[] row : cases) {
            assertEquals(row[4], jdbc.queryForObject("SELECT event_id FROM deck WHERE id = ?", String.class, row[0]),
                    row[0].toString());
        }
        var after = jdbc.queryForList("SELECT * FROM deck ORDER BY id");
        after.forEach(row -> row.remove("EVENT_ID"));
        assertEquals(before, after);
        assertEquals(cardsBefore, jdbc.queryForList("SELECT * FROM deck_card ORDER BY deck_id"));
        var firstRun = jdbc.queryForList("SELECT * FROM deck ORDER BY id");
        migration.execute(dataSource);
        assertEquals(firstRun, jdbc.queryForList("SELECT * FROM deck ORDER BY id"));
        jdbc.execute("SHUTDOWN");
    }
}
