package com.vtesdecks.api.service;

import com.vtesdecks.jpa.repositories.UserAchievementRepository;
import com.vtesdecks.jpa.repositories.UserRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class MonthlyAchievementTest {
    private JdbcTemplate jdbc;
    private AchievementService service;
    private ApiUserNotificationService notifications;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE user (id INT PRIMARY KEY)");
        jdbc.update("INSERT INTO user VALUES (7)");
        new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V1.0.72__create_user_month.sql"),
                new ClassPathResource("db/migration/V1.0.85__create_user_achievement.sql"))
                .execute(dataSource);
        notifications = mock(ApiUserNotificationService.class);
        service = new AchievementService(jdbc, mock(UserAchievementRepository.class),
                mock(UserRepository.class), notifications);
    }

    @Test
    void awardsRankingMonthForEveryRankWithoutDuplicatesOrYearShift() {
        String[] months = {"2026-08-01", "2026-09-01", "2026-12-01"};
        String[] families = {"monthly_gold", "monthly_silver", "monthly_bronze"};
        String[] expected = {"2026-08-01T00:00:00", "2026-09-01T00:00:00", "2026-12-01T00:00:00"};
        for (int i = 0; i < months.length; i++) {
            jdbc.update("INSERT INTO user_month (user_id, month_date, `rank`) VALUES (7, ?, ?)", months[i], i + 1);
        }
        jdbc.update("INSERT INTO user_month (user_id, month_date, `rank`) VALUES (7, '2026-07-01', 4)");

        evaluateMonthly();
        evaluateMonthly();

        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM user_achievement", Integer.class));
        for (int i = 0; i < months.length; i++) {
            assertEquals(LocalDateTime.parse(expected[i]), jdbc.queryForObject(
                    "SELECT earned_date FROM user_achievement WHERE family=? AND occurrence_key=?",
                    LocalDateTime.class, families[i], months[i]));
            verify(notifications).addAchievementNotification(7, families[i], 1);
        }
        verifyNoMoreInteractions(notifications);
    }

    @Test
    void preservesExistingHistoricalAwardsWithoutDuplicatesOrNotifications() {
        String[][] cases = {
                {"monthly_gold", "2026-08-01", "1"},
                {"monthly_gold", "2026-09-01", "1"},
                {"monthly_silver", "2026-12-01", "2"},
                {"monthly_bronze", "2027-01-01", "3"}
        };
        for (String[] row : cases) {
            jdbc.update("""
                    INSERT INTO user_achievement
                        (user_id, achievement_id, family, tier_value, occurrence_key, historical, earned_date)
                    VALUES (7, ?, ?, 1, ?, TRUE, ?)
                    """, row[0] + "_1", row[0], row[1], row[1]);
            jdbc.update("INSERT INTO user_month (user_id, month_date, `rank`) VALUES (7, ?, ?)",
                    row[1], Integer.parseInt(row[2]));
        }
        var original = jdbc.queryForList("SELECT * FROM user_achievement ORDER BY id");

        evaluateMonthly();
        evaluateMonthly();

        assertEquals(original, jdbc.queryForList("SELECT * FROM user_achievement ORDER BY id"));
        verifyNoMoreInteractions(notifications);
    }

    private void evaluateMonthly() {
        ReflectionTestUtils.invokeMethod(service, "awardMonthly", 7, false, true);
    }
}
