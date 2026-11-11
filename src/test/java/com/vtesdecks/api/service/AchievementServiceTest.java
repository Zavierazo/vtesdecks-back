package com.vtesdecks.api.service;

import com.vtesdecks.configuration.WebConfiguration;
import com.vtesdecks.jpa.entity.UserAchievementEntity;
import com.vtesdecks.jpa.entity.UserEntity;
import com.vtesdecks.jpa.repositories.UserAchievementRepository;
import com.vtesdecks.jpa.repositories.UserRepository;
import com.vtesdecks.model.api.ApiAchievementBadge;
import com.vtesdecks.model.api.ApiAchievementFamily;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AchievementServiceTest {
    @Mock JdbcTemplate jdbc;
    @Mock UserAchievementRepository repository;
    @Mock UserRepository userRepository;
    @Mock ApiUserNotificationService notificationService;
    @InjectMocks AchievementService service;

    @Test
    void authorBadgesUsePriorityHighestTierAndRepeatCount() {
        when(repository.findByUserIdOrderByEarnedDateAsc(7)).thenReturn(List.of(
                award("monthly_gold", 1, "2026-06"),
                award("monthly_gold", 1, "2026-07"),
                award("monthly_silver", 1, "2026-05"),
                award("monthly_bronze", 1, "2026-04"),
                award("standout_deck", 25, ""),
                award("published_decks", 50, ""),
                award("published_decks", 100, "")
        ));

        List<ApiAchievementBadge> badges = service.getBadges(7);

        assertEquals(3, badges.size());
        assertEquals("monthly_gold", badges.get(0).getFamily());
        assertEquals(2, badges.get(0).getCount());
        assertEquals("monthly_silver", badges.get(1).getFamily());
        assertEquals("monthly_bronze", badges.get(2).getFamily());
    }

    @Test
    void repeatableContributorAndAnniversaryAwardsUseMultipliers() {
        when(repository.findByUserIdOrderByEarnedDateAsc(8)).thenReturn(List.of(
                award("consistent_contributor", 1, "2026-05"),
                award("consistent_contributor", 1, "2026-06"),
                award("consistent_contributor", 1, "2026-08"),
                award("collection_cards", 100, ""),
                award("collection_cards", 500, ""),
                award("anniversary", 1, "1"),
                award("anniversary", 1, "2")
        ));

        List<ApiAchievementBadge> badges = service.getBadges(8);

        assertEquals(List.of("consistent_contributor", "collection_cards", "anniversary"),
                badges.stream().map(ApiAchievementBadge::getFamily).toList());
        assertEquals(3, badges.get(0).getCount());
        assertEquals(500, badges.get(1).getTier());
        assertEquals(2, badges.get(2).getCount());
    }

    @Test
    void adventUsesEditionYearsInProfileAndAMultiplierOnDecks() {
        UserEntity user = new UserEntity();
        user.setId(7);
        when(userRepository.findByUsername("alice")).thenReturn(user);
        when(userRepository.findById(7)).thenReturn(Optional.of(user));
        var current = award("advent_challenger", 1, "2026");
        current.setEarnedDate(LocalDateTime.parse("2026-12-04T18:15:00"));
        var backfill = award("advent_challenger", 1, "2025");
        backfill.setEarnedDate(LocalDateTime.parse("2026-12-05T08:00:00"));
        var completionist = award("advent_completionist", 1, "2025");
        completionist.setEarnedDate(backfill.getEarnedDate());
        var anniversary = award("anniversary", 1, "1");
        anniversary.setEarnedDate(LocalDateTime.parse("2026-09-20T09:00:00"));
        when(repository.findByUserIdOrderByEarnedDateAsc(7))
                .thenReturn(List.of(anniversary, current, backfill, completionist));

        for (List<ApiAchievementFamily> families : List.of(service.getPublic("alice"), service.getMine(7))) {
            var advent = families.stream().filter(family -> family.getId().equals("advent_challenger"))
                    .findFirst().orElseThrow();
            assertEquals(2, advent.getRepeatCount());
            assertEquals(List.of(ZonedDateTime.parse("2025-12-01T00:00:00Z"),
                    ZonedDateTime.parse("2026-12-04T18:15:00Z")), advent.getEarnedDates());
            assertNull(advent.getNextThreshold());
            var complete = families.stream().filter(family -> family.getId().equals("advent_completionist"))
                    .findFirst().orElseThrow();
            assertEquals(1, complete.getRepeatCount());
            assertEquals(List.of(ZonedDateTime.parse("2025-12-01T00:00:00Z")), complete.getEarnedDates());
            assertNull(complete.getNextThreshold());
            var anniversaryFamily = families.stream().filter(family -> family.getId().equals("anniversary"))
                    .findFirst().orElseThrow();
            assertEquals(List.of(ZonedDateTime.parse("2026-09-20T09:00:00Z")), anniversaryFamily.getEarnedDates());
        }
        assertEquals(2, service.getBadges(7).stream().filter(badge -> badge.getFamily().equals("advent_challenger"))
                .findFirst().orElseThrow().getCount());
    }

    @Test
    void tournamentChampionKeepsEarnedTiersAfterDisconnectingArchon() {
        UserEntity user = new UserEntity();
        user.setId(7);
        when(userRepository.findById(7)).thenReturn(Optional.of(user));
        when(repository.findByUserIdOrderByEarnedDateAsc(7)).thenReturn(List.of(
                award("tournament_champion", 1, ""), award("tournament_champion", 3, "")
        ));

        var champion = service.getMine(7).stream().filter(family -> family.getId().equals("tournament_champion"))
                .findFirst().orElseThrow();
        assertEquals(0, champion.getProgress());
        assertEquals(5, champion.getNextThreshold());
        assertEquals(List.of(1, 3), champion.getTiers().stream().filter(tier -> tier.getEarned())
                .map(tier -> tier.getThreshold()).toList());
    }

    private UserAchievementEntity award(String family, int tier, String occurrence) {
        UserAchievementEntity result = new UserAchievementEntity();
        result.setFamily(family);
        result.setAchievementId(family + "_" + tier);
        result.setTierValue(tier);
        result.setOccurrenceKey(occurrence);
        return result;
    }

    @Test
    void publicMonthlyHistoryIncludesUtcOffsetForTheExistingFrontend() throws Exception {
        UserEntity user = new UserEntity();
        user.setId(7);
        when(userRepository.findByUsername("gurttu")).thenReturn(user);
        var goldAugust = award("monthly_gold", 1, "2026-08-01");
        goldAugust.setEarnedDate(LocalDateTime.parse("2026-08-01T00:00:00"));
        var goldSeptember = award("monthly_gold", 1, "2026-09-01");
        goldSeptember.setEarnedDate(LocalDateTime.parse("2026-09-01T00:00:00"));
        var silver = award("monthly_silver", 1, "2026-09-01");
        silver.setEarnedDate(goldSeptember.getEarnedDate());
        var bronze = award("monthly_bronze", 1, "2026-09-01");
        bronze.setEarnedDate(goldSeptember.getEarnedDate());
        var contributor = award("consistent_contributor", 1, "2026-09");
        contributor.setEarnedDate(LocalDateTime.parse("2026-09-15T12:30:00"));
        when(repository.findByUserIdOrderByEarnedDateAsc(7))
                .thenReturn(List.of(goldAugust, goldSeptember, silver, bronze, contributor));

        var families = service.getPublic("gurttu");
        var json = new WebConfiguration().jacksonBuilder().build().valueToTree(families);
        for (var family : json) {
            if (family.get("id").asText().startsWith("monthly_")) {
                String expected = family.get("id").asText().equals("monthly_gold")
                        ? "2026-08-01T00:00:00Z" : "2026-09-01T00:00:00Z";
                assertEquals(expected, family.get("tiers").get(0).get("earnedDate").asText());
                assertEquals(expected, family.get("earnedDates").get(0).asText());
                for (var date : family.get("earnedDates")) {
                    var utc = OffsetDateTime.parse(date.asText()).withOffsetSameInstant(ZoneOffset.UTC);
                    assertEquals(date.asText().substring(0, 7), YearMonth.from(utc).toString());
                }
            } else {
                assertEquals("2026-09-15T12:30:00Z", family.get("earnedDates").get(0).asText());
            }
        }
        assertEquals(List.of(ZonedDateTime.parse("2026-08-01T00:00:00Z"),
                        ZonedDateTime.parse("2026-09-01T00:00:00Z")),
                families.stream().filter(f -> f.getId().equals("monthly_gold")).findFirst().orElseThrow().getEarnedDates());
    }
}
