package com.vtesdecks.jpa.repositories;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.Query;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TournamentDeckDuplicateCandidatesTest {
    @ParameterizedTest
    @CsvSource({"TOURNAMENT,false,,1", "TOURNAMENT,false,'',1", "TOURNAMENT,false,'   ',1",
            "TOURNAMENT,false,existing-id,0", "TOURNAMENT,true,,0", "COMMUNITY,false,,0"})
    void fillsOnlyEmptyVeknIdWithoutChangingAnyOtherColumn(String type, boolean deleted, String oldVeknId, int expectedUpdates) throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        try {
            jdbc.execute("CREATE TABLE deck (id VARCHAR(100) PRIMARY KEY, type VARCHAR(32), deleted BOOLEAN, vekn_id VARCHAR(64),"
                    + " name VARCHAR(255), author VARCHAR(255), tournament VARCHAR(255), source VARCHAR(255),"
                    + " final_vp DECIMAL(3,1), final_seat INT, verified BOOLEAN, views BIGINT, creation_date TIMESTAMP,"
                    + " modification_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP)");
            jdbc.update("INSERT INTO deck VALUES ('historical', ?, ?, ?, 'Curated name', 'Curated author', 'Original event',"
                            + " 'original-source', 2.0, 5, TRUE, 42, '2010-01-01 12:00:00', '2010-01-02 12:00:00')",
                    type, deleted, oldVeknId);
            Map<String, Object> expected = jdbc.queryForMap("SELECT * FROM deck WHERE id = 'historical'");
            String query = DeckRepository.class.getMethod("fillMissingVeknId", String.class, String.class)
                    .getAnnotation(Query.class).value();
            NamedParameterJdbcTemplate namedJdbc = new NamedParameterJdbcTemplate(dataSource);

            assertEquals(expectedUpdates, namedJdbc.update(query, Map.of("id", "historical", "veknId", "003120065")));
            if (expectedUpdates == 1) {
                expected.put("VEKN_ID", "003120065");
            }
            assertEquals(expected, jdbc.queryForMap("SELECT * FROM deck WHERE id = 'historical'"));

            // A concurrent or repeated fill must preserve the first stored ID and all other columns.
            assertEquals(0, namedJdbc.update(query, Map.of("id", "historical", "veknId", "different-id")));
            assertEquals(expected, jdbc.queryForMap("SELECT * FROM deck WHERE id = 'historical'"));
        } finally {
            jdbc.execute("SHUTDOWN");
        }
    }

    @Test
    void selectsOnlyActiveTournamentDecksOnTheEventDay() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        try {
            jdbc.execute("CREATE TABLE deck (id VARCHAR(100) PRIMARY KEY, type VARCHAR(32), deleted BOOLEAN, creation_date TIMESTAMP)");
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1.0.102__index_tournament_deck_dates.sql"))
                    .execute(dataSource);
            jdbc.update("INSERT INTO deck VALUES ('start', 'TOURNAMENT', FALSE, '2026-10-03 00:00:00')");
            jdbc.update("INSERT INTO deck VALUES ('end-evening', 'TOURNAMENT', FALSE, '2026-10-03 23:59:59')");
            jdbc.update("INSERT INTO deck VALUES ('event-day', 'TOURNAMENT', FALSE, '2026-10-03 12:00:00')");
            jdbc.update("INSERT INTO deck VALUES ('next-day', 'TOURNAMENT', FALSE, '2026-10-04 00:00:00')");
            jdbc.update("INSERT INTO deck VALUES ('previous-day', 'TOURNAMENT', FALSE, '2026-10-02 23:59:59')");
            jdbc.update("INSERT INTO deck VALUES ('too-early', 'TOURNAMENT', FALSE, '2026-09-30 23:59:59')");
            jdbc.update("INSERT INTO deck VALUES ('too-late', 'TOURNAMENT', FALSE, '2026-10-06 00:00:00')");
            jdbc.update("INSERT INTO deck VALUES ('community', 'COMMUNITY', FALSE, '2026-10-03 12:00:00')");
            jdbc.update("INSERT INTO deck VALUES ('deleted', 'TOURNAMENT', TRUE, '2026-10-03 12:00:00')");
            String query = DeckRepository.class.getMethod("findActiveTournamentDecksBetween", LocalDateTime.class, LocalDateTime.class)
                    .getAnnotation(Query.class).value();
            List<String> candidates = new NamedParameterJdbcTemplate(dataSource).query(query,
                    Map.of("start", LocalDateTime.of(2026, 10, 3, 0, 0), "end", LocalDateTime.of(2026, 10, 4, 0, 0)),
                    (row, index) -> row.getString("id"));

            assertEquals(List.of("end-evening", "event-day", "start"), candidates.stream().sorted().toList());
        } finally {
            jdbc.execute("SHUTDOWN");
        }
    }
}
