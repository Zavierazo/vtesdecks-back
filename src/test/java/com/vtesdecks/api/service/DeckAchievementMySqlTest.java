package com.vtesdecks.api.service;

import com.vtesdecks.jpa.entity.UserEntity;
import com.vtesdecks.jpa.repositories.UserAchievementRepository;
import com.vtesdecks.jpa.repositories.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@EnabledIfSystemProperty(named = "vtes.test.mysql.url", matches = "jdbc:mysql://127\\.0\\.0\\.1:.*")
class DeckAchievementMySqlTest {
    private static final LocalDate DECEMBER_1 = LocalDate.of(2026, 12, 1);
    private static final String FAMILY = "advent_challenger";
    private final String schema = "deck_achievement_test_" + UUID.randomUUID().toString().replace("-", "");
    private JdbcTemplate server;
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private AchievementService service;
    private ApiUserNotificationService notifications;
    private UserEntity user;

    @BeforeEach
    void createSchema() {
        String url = System.getProperty("vtes.test.mysql.url");
        server = new JdbcTemplate(new DriverManagerDataSource(url, "root", ""));
        server.execute("CREATE DATABASE " + schema);
        var dataSource = new DriverManagerDataSource(url, "root", "");
        dataSource.setCatalog(schema);
        jdbc = new JdbcTemplate(dataSource);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        jdbc.execute("CREATE TABLE user (id INT PRIMARY KEY)");
        jdbc.update("INSERT INTO user VALUES (7), (8)");
        jdbc.execute("""
                CREATE TABLE deck (id INT PRIMARY KEY AUTO_INCREMENT, user INT NOT NULL, type VARCHAR(16) NOT NULL,
                    published BOOLEAN NOT NULL, deleted BOOLEAN NOT NULL, extra JSON, event_id VARCHAR(64),
                    position INT, vekn_id VARCHAR(64), creation_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP)
                """);
        jdbc.execute("CREATE TABLE crypt (id INT PRIMARY KEY, clan VARCHAR(64))");
        jdbc.execute("CREATE TABLE deck_card (deck_id INT, id INT, number INT, PRIMARY KEY (deck_id, id))");
        jdbc.execute("CREATE TABLE comment (user INT, page_identifier VARCHAR(64), deleted BOOLEAN, creation_date TIMESTAMP)");
        jdbc.execute("CREATE TABLE reaction (user INT, target_type VARCHAR(16), target_id INT, reaction VARCHAR(16))");
        jdbc.execute("CREATE TABLE deck_user (user INT, deck_id INT, favorite BOOLEAN)");
        jdbc.execute("CREATE TABLE user_follower (followed_id INT)");
        jdbc.execute("CREATE TABLE collection (id INT PRIMARY KEY, user_id INT, deleted BOOLEAN)");
        jdbc.execute("CREATE TABLE collection_card (collection_id INT, number INT)");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1.0.85__create_user_achievement.sql"),
                new ClassPathResource("db/migration/V1.0.72__create_user_month.sql"))
                .execute(dataSource);
        notifications = mock(ApiUserNotificationService.class);
        var users = mock(UserRepository.class);
        user = new UserEntity();
        user.setId(7);
        when(users.findById(7)).thenReturn(Optional.of(user));
        service = new AchievementService(jdbc, mock(UserAchievementRepository.class), users, notifications);
    }

    @AfterEach
    void dropSchema() {
        server.execute("DROP DATABASE IF EXISTS " + schema);
    }

    @Test
    void onePublicChallengePerEditionCountsAndHistoricalBackfillIsSilent() {
        submit(2025, 24);
        evaluate(DECEMBER_1.minusDays(1));
        assertEquals(List.of("2025"), earnedYears());
        assertTrue(jdbc.queryForObject("SELECT historical FROM user_achievement", Boolean.class));
        verifyNoMoreInteractions(notifications);

        submit(2026, 1);
        submit(2026, 1);
        submit(2026, 2);
        evaluate(DECEMBER_1);
        evaluate(DECEMBER_1.plusDays(1));
        jdbc.update("UPDATE deck SET deleted=TRUE, published=FALSE");
        evaluate(DECEMBER_1.plusDays(1));

        assertEquals(List.of("2025", "2026"), earnedYears());
        verify(notifications).addAchievementNotification(7, FAMILY, 1);
        verifyNoMoreInteractions(notifications);
    }

    @Test
    void ignoresIneligibleDecksUntilTheFirstValidDayUnlocks() {
        submit(8, "COMMUNITY", true, false, "{\"year\":2026,\"day\":1}");
        submit(7, "COMMUNITY", false, false, "{\"year\":2026,\"day\":1}");
        submit(7, "COMMUNITY", true, true, "{\"year\":2026,\"day\":1}");
        submit(7, "TOURNAMENT", true, false, "{\"year\":2026,\"day\":1}");
        submit(7, "COMMUNITY", true, false, "{\"year\":2026,\"day\":\"1\"}");
        submit(7, "COMMUNITY", true, false, "{\"year\":\"2026\",\"day\":1}");
        submit(7, "COMMUNITY", true, false, "{\"year\":2026}");
        submit(7, "COMMUNITY", true, false, "null");
        submit(2026, 0);
        submit(2026, 25);
        submit(2024, 1);
        submit(4294969322L, 1);
        submit(2026, 2);

        evaluate(DECEMBER_1);
        assertEquals(List.of(), earnedYears());
        verifyNoMoreInteractions(notifications);

        evaluate(DECEMBER_1.plusDays(1));
        assertEquals(List.of("2026"), earnedYears());
        verify(notifications).addAchievementNotification(7, FAMILY, 1);
    }

    @Test
    void concurrentEvaluationsAwardAndNotifyOnlyOnce() throws Exception {
        submit(2026, 1);
        var start = new CountDownLatch(1);
        Callable<Void> worker = () -> {
            start.await();
            evaluate(DECEMBER_1);
            return null;
        };
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(worker);
            var second = workers.submit(worker);
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }
        assertEquals(List.of("2026"), earnedYears());
        verify(notifications).addAchievementNotification(7, FAMILY, 1);
        verifyNoMoreInteractions(notifications);
    }

    @Test
    void aFailedNotificationLeavesTheAwardEligibleForRetry() {
        submit(2026, 1);
        doThrow(new IllegalStateException()).when(notifications).addAchievementNotification(7, FAMILY, 1);
        assertThrows(IllegalStateException.class, () -> evaluate(DECEMBER_1));
        assertEquals(List.of(), earnedYears());

        reset(notifications);
        evaluate(DECEMBER_1);
        assertEquals(List.of("2026"), earnedYears());
        verify(notifications).addAchievementNotification(7, FAMILY, 1);
    }

    @Test
    void awardsCompletionistOnlyForAllDistinctUnlockedDaysInTheSameEdition() {
        for (int day = 1; day <= 24; day++) {
            submit(2025, day);
        }
        for (int day = 1; day <= 23; day++) {
            submit(2026, day);
        }
        submit(2026, 23);
        submit(7, "COMMUNITY", false, false, "{\"year\":2026,\"day\":24}");
        LocalDate day23 = DECEMBER_1.plusDays(22);
        evaluate(day23);
        assertEquals(List.of("2025", "2026"), earnedYears());
        assertEquals(List.of("2025"), earnedYears("advent_completionist"));
        verify(notifications).addAchievementNotification(7, FAMILY, 1);
        verifyNoMoreInteractions(notifications);

        submit(2026, 24);
        evaluate(day23);
        assertEquals(List.of("2025"), earnedYears("advent_completionist"));

        evaluate(day23.plusDays(1));
        evaluate(day23.plusDays(1));
        assertEquals(List.of("2025", "2026"), earnedYears("advent_completionist"));
        verify(notifications).addAchievementNotification(7, "advent_completionist", 1);
        verifyNoMoreInteractions(notifications);
    }

    @Test
    void clanExplorerCountsDistinctPrimaryClansByCryptCopies() {
        jdbc.update("""
                INSERT INTO crypt (id, clan) VALUES (1,'Brujah'),(2,'Brujah'),(3,'Gangrel'),(4,'Tremere'),
                    (5,'Ravnos'),(6,'Malkavian'),(7,'Ministry'),(8,'Nosferatu'),(9,'Ventrue'),(10,'Banu Haqim'),
                    (11,'Toreador'),(12,'Lasombra'),(13,'Tzimisce')
                """);
        jdbc.update("""
                INSERT INTO deck (id, user, type, published, deleted) VALUES
                    (1,7,'COMMUNITY',TRUE,FALSE),(2,7,'COMMUNITY',TRUE,FALSE),(3,7,'COMMUNITY',TRUE,FALSE),
                    (4,7,'COMMUNITY',TRUE,FALSE),(5,7,'COMMUNITY',TRUE,FALSE),(6,7,'COMMUNITY',FALSE,FALSE),
                    (7,7,'COMMUNITY',TRUE,TRUE),(8,8,'COMMUNITY',TRUE,FALSE),(9,7,'TOURNAMENT',TRUE,FALSE)
                """);
        jdbc.update("""
                INSERT INTO deck_card (deck_id, id, number) VALUES
                    (1,1,4),(1,2,3),(1,3,6),(2,3,12),(3,4,12),(4,5,6),(4,6,6),
                    (5,1,12),(5,7,0),(6,7,12),(7,8,12),(8,9,12),(9,10,12)
                """);
        assertEquals(3, progress("clan_explorer"));
        service.evaluateUser(7, false, false);
        assertEquals(List.of(3), earnedTiers("clan_explorer"));

        for (int cryptId = 5; cryptId <= 13; cryptId++) {
            int deckId = 100 + cryptId;
            jdbc.update("INSERT INTO deck (id, user, type, published, deleted) VALUES (?,7,'COMMUNITY',TRUE,FALSE)", deckId);
            jdbc.update("INSERT INTO deck_card (deck_id, id, number) VALUES (?,?,12)", deckId, cryptId);
        }
        assertEquals(12, progress("clan_explorer"));
        service.evaluateUser(7, false, false);
        service.evaluateUser(7, false, false);
        assertEquals(List.of(3, 7, 12), earnedTiers("clan_explorer"));
    }

    @Test
    void tournamentChampionCountsDistinctWinsOnlyForTheVerifiedVeknId() {
        jdbc.update("""
                INSERT INTO deck (user, type, published, deleted, position, event_id, vekn_id) VALUES
                    (8,'TOURNAMENT',TRUE,FALSE,1,'event-1','00123'),(8,'TOURNAMENT',TRUE,FALSE,1,'event-1','00123'),
                    (8,'TOURNAMENT',TRUE,FALSE,2,'event-2','00123'),(8,'TOURNAMENT',TRUE,TRUE,1,'event-3','00123'),
                    (8,'TOURNAMENT',FALSE,FALSE,1,'event-4','00123'),(7,'TOURNAMENT',TRUE,FALSE,1,'event-5','123'),
                    (7,'COMMUNITY',TRUE,FALSE,1,'event-6','00123'),(8,'TOURNAMENT',TRUE,FALSE,1,NULL,'00123'),
                    (8,'TOURNAMENT',TRUE,FALSE,1,' ','00123')
                """);
        assertEquals(0, progress("tournament_champion"));
        service.evaluateUser(7, false, false);
        assertEquals(List.of(), earnedTiers("tournament_champion"));

        user.setVeknId("00123");
        assertEquals(1, progress("tournament_champion"));
        service.evaluateUser(7, false, false);
        assertEquals(List.of(1), earnedTiers("tournament_champion"));

        for (int event = 7; event <= 10; event++) {
            jdbc.update("""
                    INSERT INTO deck (user, type, published, deleted, position, event_id, vekn_id)
                    VALUES (8,'TOURNAMENT',TRUE,FALSE,1,?,'00123')
                    """, "event-" + event);
        }
        assertEquals(5, progress("tournament_champion"));
        service.evaluateUser(7, false, false);
        user.setVeknId(null);
        service.evaluateUser(7, false, false);
        assertEquals(List.of(1, 3, 5), earnedTiers("tournament_champion"));
    }

    private void evaluate(LocalDate today) {
        transaction.executeWithoutResult(status -> service.awardAdventChallenges(7, today));
    }

    private void submit(long year, int day) {
        submit(7, "COMMUNITY", true, false, "{\"year\":%d,\"day\":%d}".formatted(year, day));
    }

    private void submit(int user, String type, boolean published, boolean deleted, String advent) {
        jdbc.update("INSERT INTO deck (user, type, published, deleted, extra) VALUES (?, ?, ?, ?, ?)",
                user, type, published, deleted, "{\"advent\":" + advent + "}");
    }

    private List<String> earnedYears() {
        return earnedYears(FAMILY);
    }

    private List<String> earnedYears(String family) {
        return jdbc.queryForList("SELECT occurrence_key FROM user_achievement WHERE family=? ORDER BY occurrence_key",
                String.class, family);
    }

    private List<Integer> earnedTiers(String family) {
        return jdbc.queryForList("SELECT tier_value FROM user_achievement WHERE family=? ORDER BY tier_value",
                Integer.class, family);
    }

    private int progress(String family) {
        return service.getMine(7).stream().filter(value -> family.equals(value.getId()))
                .findFirst().orElseThrow().getProgress();
    }
}
