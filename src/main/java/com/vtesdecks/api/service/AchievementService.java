package com.vtesdecks.api.service;

import com.vtesdecks.jpa.entity.UserAchievementEntity;
import com.vtesdecks.jpa.entity.UserEntity;
import com.vtesdecks.jpa.repositories.UserAchievementRepository;
import com.vtesdecks.jpa.repositories.UserRepository;
import com.vtesdecks.model.DeckTag;
import com.vtesdecks.model.api.ApiAchievementBadge;
import com.vtesdecks.model.api.ApiAchievementFamily;
import com.vtesdecks.model.api.ApiAchievementTier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AchievementService {
    private static final String CLAN_EXPLORER = "clan_explorer";
    private static final String TOURNAMENT_CHAMPION = "tournament_champion";
    private static final String ADVENT_CHALLENGER = "advent_challenger";
    private static final String ADVENT_COMPLETIONIST = "advent_completionist";
    private static final int ADVENT_DAY_COUNT = 24;
    private static final Map<Integer, LocalDate> ADVENT_STARTS = Map.of(
            2025, DeckTag.ADVENT_2025.getFromDate(),
            2026, DeckTag.ADVENT_2026.getFromDate());
    private static final List<Definition> CATALOG = List.of(
            tiers("published_decks", "bi-stack", 50,
                    tier(1, 5), tier(5, 5), tier(10, 10), tier(25, 10), tier(50, 15),
                    tier(100, 15), tier(250, 20), tier(500, 25), tier(1000, 35)),
            tiers(CLAN_EXPLORER, "bi-compass", 55, tier(3, 15), tier(7, 30), tier(12, 55)),
            tiers(TOURNAMENT_CHAMPION, "bi-trophy", 5, tier(1, 60), tier(3, 80), tier(5, 110)),
            tiers("deck_comments", "bi-chat-left-text", 60,
                    tier(1, 2), tier(5, 3), tier(10, 5), tier(25, 8), tier(50, 12), tier(100, 20)),
            tiers("deck_appreciation", "bi-stars", 30,
                    tier(1, 3), tier(5, 5), tier(10, 8), tier(25, 12), tier(50, 20)),
            tiers("community_favorite", "bi-bookmark-star", 20,
                    tier(5, 8), tier(10, 12), tier(25, 20), tier(50, 25), tier(100, 35)),
            tiers("followers", "bi-people", 70,
                    tier(5, 3), tier(10, 5), tier(25, 8), tier(50, 12), tier(100, 20)),
            tiers("conversation_starter", "bi-chat-heart", 40, tier(5, 8), tier(10, 15), tier(25, 30)),
            tiers("standout_deck", "bi-gem", 10, tier(5, 10), tier(10, 15), tier(25, 35)),
            tiers("consistent_contributor", "bi-calendar-check", 45, tier(1, 1)),
            tiers("collection_cards", "bi-collection", 80,
                    tier(100, 2), tier(500, 3), tier(1000, 5), tier(2500, 5), tier(5000, 5),
                    tier(10000, 8), tier(25000, 10), tier(50000, 12), tier(100000, 15)),
            tiers("anniversary", "bi-hourglass-split", 90, tier(1, 3)),
            tiers(ADVENT_CHALLENGER, "bi-gift", 95, tier(1, 5)),
            tiers(ADVENT_COMPLETIONIST, "bi-calendar2-check", 85, tier(1, 65)),
            tiers("monthly_gold", "bi-trophy-fill", 1, tier(1, 15)),
            tiers("monthly_silver", "bi-award-fill", 2, tier(1, 10)),
            tiers("monthly_bronze", "bi-award", 3, tier(1, 5))
    );
    private static final Map<String, Integer> POINTS_BY_ACHIEVEMENT = CATALOG.stream()
            .flatMap(definition -> definition.tiers.stream()
                    .map(tier -> Map.entry(definition.achievementId(tier.threshold), tier.points)))
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

    private final JdbcTemplate jdbc;
    private final UserAchievementRepository repository;
    private final UserRepository userRepository;
    private final ApiUserNotificationService notificationService;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void activity(Integer... userIds) {
        for (Integer userId : userIds) {
            if (userId == null) {
                continue;
            }
            try {
                evaluateUser(userId, true, false);
            } catch (RuntimeException e) {
                log.error("Could not evaluate achievements for user {}", userId, e);
            }
        }
    }

    private static Definition tiers(String family, String icon, int priority, Tier... tiers) {
        return new Definition(family, icon, priority, List.of(tiers));
    }

    private static Tier tier(int threshold, int points) {
        return new Tier(threshold, points);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void evaluateUser(Integer userId, boolean notify, boolean historical) {
        UserEntity user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return;
        }
        Map<String, Integer> progress = progress(user);
        for (Definition definition : CATALOG) {
            if (isRepeatable(definition.family)) {
                continue;
            }
            int value = progress.getOrDefault(definition.family, 0);
            List<Integer> newlyEarned = new ArrayList<>();
            for (Tier tier : definition.tiers) {
                int threshold = tier.threshold;
                if (value >= threshold && award(userId, definition.achievementId(threshold),
                        definition.family, threshold, "", historical, null)) {
                    newlyEarned.add(threshold);
                }
            }
            if (notify && !newlyEarned.isEmpty()) {
                notificationService.addAchievementNotification(userId, definition.family,
                        newlyEarned.get(newlyEarned.size() - 1));
            }
        }
        awardContributorMonths(userId, historical, notify);
        awardAnniversaries(user, historical, notify);
        awardMonthly(userId, historical, notify);
    }

    public List<ApiAchievementFamily> getPublic(String username) {
        UserEntity user = userRepository.findByUsername(username);
        if (user == null) {
            return null;
        }
        return build(user, false).stream()
                .filter(family -> family.getTiers().stream().anyMatch(ApiAchievementTier::getEarned))
                .toList();
    }

    public List<ApiAchievementFamily> getMine(Integer userId) {
        UserEntity user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return List.of();
        }
        return build(user, true);
    }

    public int getPoints(Integer userId) {
        return pointsFromAwards(repository.findByUserIdOrderByEarnedDateAsc(userId));
    }

    public AuthorAchievements getAuthorAchievements(Integer userId) {
        var awards = repository.findByUserIdOrderByEarnedDateAsc(userId);
        return new AuthorAchievements(pointsFromAwards(awards), badgesFromAwards(awards));
    }

    private int pointsFromAwards(List<UserAchievementEntity> awards) {
        return awards.stream()
                .mapToInt(award -> POINTS_BY_ACHIEVEMENT.getOrDefault(award.getAchievementId(), 0))
                .sum();
    }

    private List<ApiAchievementBadge> badgesFromAwards(List<UserAchievementEntity> awards) {
        Map<String, List<UserAchievementEntity>> byFamily = awards.stream()
                .collect(Collectors.groupingBy(UserAchievementEntity::getFamily));
        return CATALOG.stream().sorted(Comparator.comparingInt(Definition::priority))
                .map(d -> Map.entry(d, byFamily.getOrDefault(d.family, List.of())))
                .filter(entry -> !entry.getValue().isEmpty())
                .limit(3)
                .map(entry -> {
                    UserAchievementEntity highest = entry.getValue().stream()
                            .max(Comparator.comparingInt(UserAchievementEntity::getTierValue)).orElseThrow();
                    Integer count = isRepeatable(entry.getKey().family) ? entry.getValue().size() : null;
                    return ApiAchievementBadge.builder().family(entry.getKey().family)
                            .achievementId(highest.getAchievementId()).tier(highest.getTierValue())
                            .count(count).icon(entry.getKey().icon).build();
                })
                .toList();
    }

    private static boolean isRepeatable(String family) {
        return family.startsWith("monthly_") || family.equals("consistent_contributor") ||
                family.equals("anniversary") || isAdventFamily(family);
    }

    private static boolean isAdventFamily(String family) {
        return ADVENT_CHALLENGER.equals(family) || ADVENT_COMPLETIONIST.equals(family);
    }

    private List<ApiAchievementFamily> build(UserEntity user, boolean includeProgress) {
        if (user == null) {
            return List.of();
        }
        Map<String, Integer> progress = includeProgress ? progress(user) : Map.of();
        Map<String, List<UserAchievementEntity>> earned = repository.findByUserIdOrderByEarnedDateAsc(user.getId()).stream()
                .collect(Collectors.groupingBy(UserAchievementEntity::getFamily));
        List<ApiAchievementFamily> result = new ArrayList<>();
        for (Definition d : CATALOG) {
            List<UserAchievementEntity> familyAwards = earned.getOrDefault(d.family, List.of());
            boolean repeatable = isRepeatable(d.family);
            int repeatCount = repeatable ? familyAwards.size() : 0;
            List<ApiAchievementTier> tiers = d.tiers.stream().map(tier -> {
                int threshold = tier.threshold;
                UserAchievementEntity award = familyAwards.stream()
                        .filter(a -> a.getTierValue().equals(threshold))
                        .findFirst()
                        .orElse(null);
                return ApiAchievementTier.builder()
                        .id(d.achievementId(threshold))
                        .threshold(threshold)
                        .points(tier.points)
                        .earned(award != null)
                        .earnedDate(award == null ? null : apiEarnedDate(award))
                        .historical(award == null ? null : award.getHistorical())
                        .build();
            }).filter(tier -> includeProgress || tier.getEarned())
                    .toList();
            Integer current = includeProgress && !isAdventFamily(d.family) ? progress.getOrDefault(d.family, 0) : null;
            Integer next = current != null
                    ? tiers.stream().filter(tier -> !tier.getEarned() && tier.getThreshold() > current)
                            .map(ApiAchievementTier::getThreshold).findFirst().orElse(null)
                    : null;
            result.add(ApiAchievementFamily.builder()
                    .id(d.family)
                    .icon(d.icon)
                    .progress(current)
                    .nextThreshold(next)
                    .repeatCount(repeatCount == 0 ? null : repeatCount)
                    .earnedPoints(pointsFromAwards(familyAwards))
                    .pointsPerOccurrence(repeatable ? d.tiers.getFirst().points : null)
                    .tiers(tiers)
                    .earnedDates(familyAwards.stream()
                            .map(this::apiEarnedDate)
                            .filter(Objects::nonNull)
                            .sorted()
                            .toList())
                    .build());
        }
        return result;
    }

    private ZonedDateTime apiEarnedDate(UserAchievementEntity award) {
        LocalDateTime earnedDate = award.getEarnedDate();
        if (isAdventFamily(award.getFamily())) {
            int edition = Integer.parseInt(award.getOccurrenceKey());
            if (earnedDate == null || earnedDate.getYear() != edition) {
                // Anchor awards recorded in a later year to the Advent edition.
                earnedDate = LocalDate.of(edition, 12, 1).atStartOfDay();
            }
        }
        if (earnedDate == null) {
            return null;
        }
        // The profile formats award months in UTC. An offset-less midnight would
        // be interpreted in the browser's zone and can fall in the previous month.
        return earnedDate.atZone(ZoneOffset.UTC);
    }

    private Map<String, Integer> progress(UserEntity user) {
        int id = user.getId();
        Map<String, Integer> p = new HashMap<>();
        p.put("published_decks", scalar("""
                SELECT COUNT(DISTINCT d.id)
                FROM deck d
                WHERE d.user=? AND d.type='COMMUNITY' AND d.published=TRUE AND d.deleted=FALSE
                  AND EXISTS (SELECT 1 FROM deck_card dc WHERE dc.deck_id=d.id AND dc.number>0)
                """, id));
        p.put(CLAN_EXPLORER, countPrimaryClans(id));
        p.put(TOURNAMENT_CHAMPION, countTournamentWins(user.getVeknId()));
        p.put("deck_comments", scalar("""
                SELECT COUNT(DISTINCT c.page_identifier)
                FROM comment c
                JOIN deck d ON c.page_identifier=CONCAT('deck_', d.id)
                WHERE c.user=? AND c.deleted=FALSE AND d.user<>?
                  AND d.published=TRUE AND d.deleted=FALSE
                """, id, id));
        p.put("deck_appreciation", scalar("""
                SELECT COUNT(DISTINCT r.user)
                FROM reaction r
                JOIN deck d ON d.id=r.target_id
                WHERE r.target_type='DECK' AND r.reaction<>'TOO_GREEDY'
                  AND d.user=? AND r.user<>?
                  AND d.type='COMMUNITY' AND d.published=TRUE AND d.deleted=FALSE
                """, id, id));
        p.put("community_favorite", scalar("""
                SELECT COUNT(DISTINCT du.user)
                FROM deck_user du
                JOIN deck d ON d.id=du.deck_id
                WHERE du.favorite=TRUE AND d.user=? AND du.user<>?
                  AND d.type='COMMUNITY' AND d.published=TRUE AND d.deleted=FALSE
                """, id, id));
        p.put("followers", scalar("""
                SELECT COUNT(*)
                FROM user_follower
                WHERE followed_id=?
                """, id));
        p.put("conversation_starter", scalar("""
                SELECT COUNT(DISTINCT c.user)
                FROM comment c
                JOIN deck d ON c.page_identifier=CONCAT('deck_', d.id)
                WHERE d.user=? AND c.user<>? AND c.deleted=FALSE
                  AND d.published=TRUE AND d.deleted=FALSE
                """, id, id));
        p.put("standout_deck", scalar("""
                SELECT COALESCE(MAX(x.c),0)
                FROM (
                    SELECT COUNT(DISTINCT r.user) c
                    FROM reaction r
                    JOIN deck d ON d.id=r.target_id
                    WHERE r.target_type='DECK' AND r.reaction<>'TOO_GREEDY'
                      AND d.user=? AND r.user<>? AND d.published=TRUE AND d.deleted=FALSE
                    GROUP BY d.id
                ) x
                """, id, id));
        p.put("consistent_contributor", scalar("""
                SELECT COUNT(*)
                FROM (
                    SELECT DATE_FORMAT(d.creation_date,'%Y-%m') m
                    FROM deck d
                    WHERE d.user=? AND d.type='COMMUNITY' AND d.published=TRUE AND d.deleted=FALSE
                    UNION
                    SELECT DATE_FORMAT(c.creation_date,'%Y-%m') m
                    FROM comment c
                    WHERE c.user=? AND c.deleted=FALSE
                ) activity_months
                """, id, id));
        p.put("collection_cards", scalar("""
                SELECT COALESCE(SUM(cc.number),0)
                FROM collection c
                JOIN collection_card cc ON cc.collection_id=c.id
                WHERE c.user_id=? AND c.deleted=FALSE AND cc.number>0
                """, id));
        p.put("anniversary", user.getCreationDate() == null ? 0 :
                Math.max(0, Period.between(user.getCreationDate().toLocalDate(), LocalDate.now(ZoneOffset.UTC)).getYears()));
        return p;
    }

    private int countPrimaryClans(int userId) {
        return scalar("""
                WITH clan_counts AS (
                    SELECT d.id AS deck_id, TRIM(c.clan) AS clan, SUM(dc.number) AS copies
                    FROM deck d
                    JOIN deck_card dc ON dc.deck_id=d.id
                    JOIN crypt c ON c.id=dc.id
                    WHERE d.user=? AND d.type='COMMUNITY' AND d.published=TRUE AND d.deleted=FALSE
                      AND dc.number>0 AND NULLIF(TRIM(c.clan), '') IS NOT NULL
                    GROUP BY d.id, TRIM(c.clan)
                ), ranked_clans AS (
                    SELECT clan, RANK() OVER (PARTITION BY deck_id ORDER BY copies DESC) AS clan_rank,
                           COUNT(*) OVER (PARTITION BY deck_id, copies) AS tied_clans
                    FROM clan_counts
                )
                SELECT COUNT(DISTINCT clan) FROM ranked_clans WHERE clan_rank=1 AND tied_clans=1
                """, userId);
    }

    private int countTournamentWins(String verifiedVeknId) {
        if (verifiedVeknId == null || verifiedVeknId.isBlank()) {
            return 0;
        }
        return scalar("""
                SELECT COUNT(DISTINCT NULLIF(TRIM(event_id), ''))
                FROM deck
                WHERE vekn_id=? AND type='TOURNAMENT' AND position=1 AND published=TRUE AND deleted=FALSE
                """, verifiedVeknId);
    }

    private int scalar(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private void awardContributorMonths(Integer userId, boolean historical, boolean notify) {
        List<LocalDateTime> months = jdbc.query("""
                        SELECT MIN(activity_date) activity_date
                        FROM (
                            SELECT d.creation_date activity_date
                            FROM deck d
                            WHERE d.user=? AND d.type='COMMUNITY'
                              AND d.published=TRUE AND d.deleted=FALSE
                            UNION ALL
                            SELECT c.creation_date activity_date
                            FROM comment c
                            WHERE c.user=? AND c.deleted=FALSE
                        ) activities
                        GROUP BY DATE_FORMAT(activity_date,'%Y-%m')
                        ORDER BY activity_date
                        """,
                (rs, rowNum) -> rs.getTimestamp("activity_date").toLocalDateTime(), userId, userId);
        boolean newlyEarned = false;
        for (LocalDateTime activityDate : months) {
            String occurrence = activityDate.getYear() + "-" + String.format("%02d", activityDate.getMonthValue());
            newlyEarned |= award(userId, "consistent_contributor_1", "consistent_contributor", 1,
                    occurrence, historical, activityDate);
        }
        if (notify && newlyEarned) {
            notificationService.addAchievementNotification(userId, "consistent_contributor", 1);
        }
    }

    private void awardAnniversaries(UserEntity user, boolean historical, boolean notify) {
        if (user.getCreationDate() == null) {
            return;
        }
        int completedYears = Math.max(0, Period.between(user.getCreationDate().toLocalDate(),
                LocalDate.now(ZoneOffset.UTC)).getYears());
        boolean newlyEarned = false;
        for (int year = 1; year <= completedYears; year++) {
            LocalDateTime anniversaryDate = user.getCreationDate().plusYears(year);
            newlyEarned |= award(user.getId(), "anniversary_1", "anniversary", 1,
                    Integer.toString(year), historical, anniversaryDate);
        }
        if (notify && newlyEarned) {
            notificationService.addAchievementNotification(user.getId(), "anniversary", 1);
        }
    }

    private void awardMonthly(Integer userId, boolean historical, boolean notify) {
        jdbc.query("SELECT month_date, `rank` FROM user_month WHERE user_id=? AND `rank`<=3", rs -> {
            int rank = rs.getInt("rank");
            String family = rank == 1 ? "monthly_gold" : rank == 2 ? "monthly_silver" : "monthly_bronze";
            LocalDate rankingMonth = rs.getObject("month_date", LocalDate.class);
            // The achievement belongs to the ranked month, not the month when the job runs.
            String key = rankingMonth.toString();
            LocalDateTime earnedDate = rankingMonth.atStartOfDay();
            if (award(userId, family + "_1", family, 1, key, historical, earnedDate) && notify) {
                notificationService.addAchievementNotification(userId, family, 1);
            }
        }, userId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void awardAdventChallenges(Integer userId) {
        awardAdventChallenges(userId, LocalDate.now(ZoneOffset.UTC));
    }

    void awardAdventChallenges(Integer userId, LocalDate today) {
        Map<Integer, Integer> completedDays = new TreeMap<>();
        jdbc.query("""
                        SELECT DISTINCT CAST(JSON_EXTRACT(extra, '$.advent.year') AS SIGNED) AS edition_year,
                               CAST(JSON_EXTRACT(extra, '$.advent.day') AS SIGNED) AS challenge_day
                        FROM deck
                        WHERE user=? AND type='COMMUNITY' AND published=TRUE AND deleted=FALSE
                          AND JSON_TYPE(JSON_EXTRACT(extra, '$.advent.year')) = 'INTEGER'
                          AND JSON_TYPE(JSON_EXTRACT(extra, '$.advent.day')) = 'INTEGER'
                          AND CAST(JSON_EXTRACT(extra, '$.advent.year') AS SIGNED) BETWEEN 1000 AND 9999
                          AND CAST(JSON_EXTRACT(extra, '$.advent.day') AS SIGNED) BETWEEN 1 AND ?
                        """,
                rs -> {
                    LocalDate start = ADVENT_STARTS.get(rs.getInt("edition_year"));
                    if (start != null && !today.isBefore(start.plusDays(rs.getInt("challenge_day") - 1L))) {
                        completedDays.merge(start.getYear(), 1, Integer::sum);
                    }
                }, userId, ADVENT_DAY_COUNT);
        for (var edition : completedDays.entrySet()) {
            Integer year = edition.getKey();
            boolean historical = year < today.getYear();
            awardAdventEdition(userId, ADVENT_CHALLENGER, year, historical);
            if (edition.getValue() == ADVENT_DAY_COUNT) {
                awardAdventEdition(userId, ADVENT_COMPLETIONIST, year, historical);
            }
        }
    }

    private void awardAdventEdition(Integer userId, String family, Integer year, boolean historical) {
        if (award(userId, family + "_1", family, 1, year.toString(), historical, null) && !historical) {
            notificationService.addAchievementNotification(userId, family, 1);
        }
    }

    private boolean award(Integer userId, String achievementId, String family, int tier, String key,
                          boolean historical, LocalDateTime earnedDate) {
        return jdbc.update("""
                        INSERT IGNORE INTO user_achievement
                            (user_id, achievement_id, family, tier_value, occurrence_key,
                             historical, earned_date)
                        VALUES (?, ?, ?, ?, ?, ?, COALESCE(?, CURRENT_TIMESTAMP))
                        """,
                userId, achievementId, family, tier, key, historical, earnedDate) == 1;
    }

    public record AuthorAchievements(int points, List<ApiAchievementBadge> badges) {
    }

    private record Definition(String family, String icon, int priority, List<Tier> tiers) {
        private String achievementId(int threshold) {
            return family + "_" + threshold;
        }
    }

    private record Tier(int threshold, int points) {
    }
}
