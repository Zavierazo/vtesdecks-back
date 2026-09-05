package com.vtesdecks.api.service;

import com.vtesdecks.jpa.entity.UserAchievementEntity;
import com.vtesdecks.jpa.entity.UserEntity;
import com.vtesdecks.jpa.repositories.UserAchievementRepository;
import com.vtesdecks.jpa.repositories.UserRepository;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AchievementService {
    private static final List<Definition> CATALOG = List.of(
            tiers("published_decks", "bi-stack", 50, 1, 5, 10, 25, 50, 100, 250, 500, 1000),
            tiers("deck_comments", "bi-chat-left-text", 60, 1, 5, 10, 25, 50, 100),
            tiers("deck_appreciation", "bi-stars", 30, 1, 5, 10, 25, 50),
            tiers("community_favorite", "bi-bookmark-star", 20, 5, 10, 25, 50, 100),
            tiers("followers", "bi-people", 70, 5, 10, 25, 50, 100),
            tiers("conversation_starter", "bi-chat-heart", 40, 5, 10, 25),
            tiers("standout_deck", "bi-gem", 10, 5, 10, 25),
            tiers("consistent_contributor", "bi-calendar-check", 45, 1),
            tiers("collection_cards", "bi-collection", 80,
                    100, 500, 1000, 2500, 5000, 10000, 25000, 50000, 100000),
            tiers("anniversary", "bi-hourglass-split", 90, 1),
            tiers("monthly_gold", "bi-trophy-fill", 1, 1),
            tiers("monthly_silver", "bi-award-fill", 2, 1),
            tiers("monthly_bronze", "bi-award", 3, 1)
    );

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

    private static Definition tiers(String family, String icon, int priority, int... thresholds) {
        return new Definition(family, icon, priority, Arrays.stream(thresholds).boxed().toList());
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
            for (Integer threshold : definition.thresholds) {
                if (value >= threshold && award(userId, definition.family + "_" + threshold,
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

    public List<ApiAchievementBadge> getBadges(Integer userId) {
        return badgesFromAwards(repository.findByUserIdOrderByEarnedDateAsc(userId));
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
                family.equals("anniversary");
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
            int repeatCount = isRepeatable(d.family) ? familyAwards.size() : 0;
            List<ApiAchievementTier> tiers = d.thresholds.stream().map(threshold -> {
                UserAchievementEntity award = familyAwards.stream()
                        .filter(a -> a.getTierValue().equals(threshold))
                        .findFirst()
                        .orElse(null);
                return ApiAchievementTier.builder()
                        .id(d.family + "_" + threshold)
                        .threshold(threshold)
                        .earned(award != null)
                        .earnedDate(award == null ? null : award.getEarnedDate())
                        .historical(award == null ? null : award.getHistorical())
                        .build();
            }).filter(tier -> includeProgress || tier.getEarned())
                    .toList();
            Integer current = includeProgress ? progress.getOrDefault(d.family, 0) : null;
            Integer next = includeProgress
                    ? d.thresholds.stream().filter(t -> t > current).findFirst().orElse(null)
                    : null;
            result.add(ApiAchievementFamily.builder()
                    .id(d.family)
                    .icon(d.icon)
                    .progress(current)
                    .nextThreshold(next)
                    .repeatCount(repeatCount == 0 ? null : repeatCount)
                    .tiers(tiers)
                    .earnedDates(familyAwards.stream()
                            .map(UserAchievementEntity::getEarnedDate)
                            .filter(Objects::nonNull)
                            .toList())
                    .build());
        }
        return result;
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
            String key = rs.getDate("month_date").toLocalDate().toString();
            if (award(userId, family + "_1", family, 1, key, historical, rs.getTimestamp("month_date").toLocalDateTime()) && notify) {
                notificationService.addAchievementNotification(userId, family, 1);
            }
        }, userId);
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

    private record Definition(String family, String icon, int priority, List<Integer> thresholds) {
    }
}
