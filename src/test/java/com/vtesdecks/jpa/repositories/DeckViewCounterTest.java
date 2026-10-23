package com.vtesdecks.jpa.repositories;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.Timestamp;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeckViewCounterTest {
    @Test
    void incrementsViewsWhilePreservingAutomaticModificationTimestamp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:deckViewCounter;MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE deck (id VARCHAR(255) PRIMARY KEY, views BIGINT NOT NULL, "
                + "modification_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP)");
        Timestamp original = Timestamp.valueOf("2026-01-01 12:00:00");
        jdbc.update("INSERT INTO deck (id, views, modification_date) VALUES (?, ?, ?)",
                "preconstructed-noites_brasileiras", 10L, original);
        String query = DeckRepository.class.getMethod("incrementViews", String.class, int.class)
                .getAnnotation(Query.class).value();
        NamedParameterJdbcTemplate namedJdbc = new NamedParameterJdbcTemplate(dataSource);

        assertEquals(1, namedJdbc.update(query, Map.of("id", "preconstructed-noites_brasileiras", "views", 2)));
        assertEquals(12L, jdbc.queryForObject("SELECT views FROM deck", Long.class));
        assertEquals(original, jdbc.queryForObject("SELECT modification_date FROM deck", Timestamp.class));
        assertEquals(0, namedJdbc.update(query, Map.of("id", "missing-deck", "views", 2)));

        // Ordinary updates must still advance the timestamp.
        jdbc.update("UPDATE deck SET views = views + 1");
        org.junit.jupiter.api.Assertions.assertTrue(
                jdbc.queryForObject("SELECT modification_date FROM deck", Timestamp.class).after(original));
    }
}
