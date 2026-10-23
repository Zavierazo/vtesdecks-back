package com.vtesdecks.api.service;

import com.vtesdecks.configuration.WebConfiguration;
import com.vtesdecks.jpa.entity.UserAchievementEntity;
import com.vtesdecks.jpa.entity.UserEntity;
import com.vtesdecks.jpa.repositories.UserAchievementRepository;
import com.vtesdecks.jpa.repositories.UserRepository;
import com.vtesdecks.model.api.ApiAchievementBadge;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
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
