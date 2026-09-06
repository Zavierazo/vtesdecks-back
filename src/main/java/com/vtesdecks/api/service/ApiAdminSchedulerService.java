package com.vtesdecks.api.service;

import com.vtesdecks.cache.DeckArchetypeIndex;
import com.vtesdecks.model.api.ApiAdminScheduler;
import com.vtesdecks.scheduler.AchievementScheduler;
import com.vtesdecks.scheduler.CleanUpScheduler;
import com.vtesdecks.scheduler.DeckArchetypeScheduler;
import com.vtesdecks.scheduler.PatreonReminderScheduler;
import com.vtesdecks.scheduler.ProxyCardOptionScheduler;
import com.vtesdecks.scheduler.UserMonthScheduler;
import com.vtesdecks.scheduler.VtesdleTodayScheduler;
import com.vtesdecks.scheduler.shops.CardGameGeekScheduler;
import com.vtesdecks.scheduler.shops.DriveThruCardsScheduler;
import com.vtesdecks.scheduler.shops.GamePodScheduler;
import com.vtesdecks.scheduler.shops.MarketScheduler;
import com.vtesdecks.scheduler.tournament.TournamentDeckScheduler;
import com.vtesdecks.scheduler.tournament.TournamentEternalVigilanceDeckScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApiAdminSchedulerService {
    private static final List<ApiAdminScheduler> SCHEDULERS = List.of(
            new ApiAdminScheduler("deck-views-clean", "Clean deck views"),
            new ApiAdminScheduler("deck-clean", "Clean decks"),
            new ApiAdminScheduler("collection-clean", "Clean collections"),
            new ApiAdminScheduler("comments-clean", "Clean comments"),
            new ApiAdminScheduler("reactions-clean", "Clean orphan reactions"),
            new ApiAdminScheduler("notifications-clean", "Clean notifications"),
            new ApiAdminScheduler("scrap-decks", "Import tournament decks"),
            new ApiAdminScheduler("scrap-decks-eternal-vigilance", "Import Eternal Vigilance decks"),
            new ApiAdminScheduler("drive-thru-cards", "Synchronize DriveThruCards"),
            new ApiAdminScheduler("game-pod", "Synchronize Game Pod"),
            new ApiAdminScheduler("card-game-geek", "Synchronize Card Game Geek"),
            new ApiAdminScheduler("market", "Synchronize market prices"),
            new ApiAdminScheduler("vtesdle-today", "Select today's Vtesdle card"),
            new ApiAdminScheduler("proxy-card-options", "Refresh proxy card options"),
            new ApiAdminScheduler("deck-archetypes", "Refresh deck archetypes"),
            new ApiAdminScheduler("deck-archetype-index", "Rebuild deck archetype index"),
            new ApiAdminScheduler("user-month", "Calculate users of the month"),
            new ApiAdminScheduler("achievements", "Reconcile achievements"),
            new ApiAdminScheduler("patreon-reminder", "Send Patreon reminders")
    );

    private final CleanUpScheduler cleanUpScheduler;
    private final TournamentDeckScheduler tournamentDeckScheduler;
    private final TournamentEternalVigilanceDeckScheduler tournamentEternalVigilanceDeckScheduler;
    private final DriveThruCardsScheduler driveThruCardsScheduler;
    private final GamePodScheduler gamePodScheduler;
    private final VtesdleTodayScheduler vtesdleTodayScheduler;
    private final CardGameGeekScheduler cardGameGeekScheduler;
    private final ProxyCardOptionScheduler proxyCardOptionScheduler;
    private final MarketScheduler marketScheduler;
    private final DeckArchetypeScheduler deckArchetypeScheduler;
    private final DeckArchetypeIndex deckArchetypeIndex;
    private final UserMonthScheduler userMonthScheduler;
    private final AchievementScheduler achievementScheduler;
    private final PatreonReminderScheduler patreonReminderScheduler;

    public List<ApiAdminScheduler> getAll() {
        return SCHEDULERS;
    }

    public boolean run(String key, Integer actorUserId) {
        log.info("Manual scheduler requested actorUserId={} scheduler={}", actorUserId, key);
        switch (key) {
            case "deck-views-clean" -> cleanUpScheduler.deckViewCleanScheduler();
            case "deck-clean" -> cleanUpScheduler.deckCleanScheduler();
            case "collection-clean" -> cleanUpScheduler.collectionCleanScheduler();
            case "comments-clean" -> cleanUpScheduler.commentsCleanScheduler();
            case "reactions-clean" -> cleanUpScheduler.reactionsCleanScheduler();
            case "notifications-clean" -> cleanUpScheduler.notificationsCleanScheduler();
            case "scrap-decks" -> tournamentDeckScheduler.scrappingDecks();
            case "scrap-decks-eternal-vigilance" -> tournamentEternalVigilanceDeckScheduler.scrappingDecks();
            case "drive-thru-cards" -> driveThruCardsScheduler.scrapCards();
            case "game-pod" -> gamePodScheduler.scrapCards();
            case "card-game-geek" -> cardGameGeekScheduler.scrapCards();
            case "market" -> marketScheduler.scrapCards();
            case "vtesdle-today" -> vtesdleTodayScheduler.selectTodayVtesdle();
            case "proxy-card-options" -> proxyCardOptionScheduler.proxyCardOptionScheduler();
            case "deck-archetypes" -> deckArchetypeScheduler.deckArchetypeScheduler();
            case "deck-archetype-index" -> deckArchetypeIndex.refreshIndex();
            case "user-month" -> userMonthScheduler.selectUsersOfMonth();
            case "achievements" -> achievementScheduler.reconcile();
            case "patreon-reminder" -> patreonReminderScheduler.remindPatreon();
            default -> {
                return false;
            }
        }
        log.info("Manual scheduler completed actorUserId={} scheduler={}", actorUserId, key);
        return true;
    }
}
