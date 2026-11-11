package com.vtesdecks.api.service;

import com.vtesdecks.cache.DeckArchetypeIndex;
import com.vtesdecks.cache.DeckIndex;
import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.model.api.ApiAdminScheduler;
import com.vtesdecks.scheduler.AchievementScheduler;
import com.vtesdecks.scheduler.ArchonUserScheduler;
import com.vtesdecks.scheduler.CleanUpScheduler;
import com.vtesdecks.scheduler.DeckArchetypeScheduler;
import com.vtesdecks.scheduler.I18nImageScheduler;
import com.vtesdecks.scheduler.PatreonReminderScheduler;
import com.vtesdecks.scheduler.PatreonSupporterScheduler;
import com.vtesdecks.scheduler.ProxyCardOptionScheduler;
import com.vtesdecks.scheduler.UserMonthScheduler;
import com.vtesdecks.scheduler.VtesdleTodayScheduler;
import com.vtesdecks.scheduler.shops.MarketScheduler;
import com.vtesdecks.scheduler.tournament.TournamentDeckScheduler;
import com.vtesdecks.scheduler.tournament.TournamentArchonDeckScheduler;
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
            new ApiAdminScheduler("email-actions-clean", "Clean expired email action tokens"),
            new ApiAdminScheduler("twda-decks", "Import tournament decks"),
            new ApiAdminScheduler("twda-archon-decks", "Import Archon finalist decks"),
            new ApiAdminScheduler("archon-users", "Synchronize Archon members"),
            new ApiAdminScheduler("market", "Synchronize market prices"),
            new ApiAdminScheduler("vtesdle-today", "Select today's Vtesdle card"),
            new ApiAdminScheduler("proxy-card-options", "Refresh proxy card options"),
            new ApiAdminScheduler("i18n-images", "Discover translated card images"),
            new ApiAdminScheduler("deck-index", "Refresh deck index"),
            new ApiAdminScheduler("crypt-index", "Refresh crypt index"),
            new ApiAdminScheduler("library-index", "Refresh library index"),
            new ApiAdminScheduler("deck-archetypes", "Refresh deck archetypes"),
            new ApiAdminScheduler("deck-archetype-index", "Rebuild deck archetype index"),
            new ApiAdminScheduler("user-month", "Calculate users of the month"),
            new ApiAdminScheduler("achievements", "Reconcile achievements"),
            new ApiAdminScheduler("patreon-supporters", "Synchronize Patreon supporters"),
            new ApiAdminScheduler("patreon-reminder", "Send Patreon reminders")
    );

    private final CleanUpScheduler cleanUpScheduler;
    private final TournamentDeckScheduler tournamentDeckScheduler;
    private final TournamentArchonDeckScheduler tournamentArchonDeckScheduler;
    private final ArchonUserScheduler archonUserScheduler;
    private final VtesdleTodayScheduler vtesdleTodayScheduler;
    private final ProxyCardOptionScheduler proxyCardOptionScheduler;
    private final I18nImageScheduler i18nImageScheduler;
    private final DeckIndex deckIndex;
    private final CryptCache cryptCache;
    private final LibraryCache libraryCache;
    private final MarketScheduler marketScheduler;
    private final DeckArchetypeScheduler deckArchetypeScheduler;
    private final DeckArchetypeIndex deckArchetypeIndex;
    private final UserMonthScheduler userMonthScheduler;
    private final AchievementScheduler achievementScheduler;
    private final PatreonReminderScheduler patreonReminderScheduler;
    private final PatreonSupporterScheduler patreonSupporterScheduler;

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
            case "email-actions-clean" -> cleanUpScheduler.emailActionsCleanScheduler();
            case "twda-decks" -> tournamentDeckScheduler.scrappingDecks();
            case "twda-archon-decks" -> tournamentArchonDeckScheduler.scrappingDecks();
            case "archon-users" -> archonUserScheduler.scrappingUsers();
            case "market" -> marketScheduler.scrapCards();
            case "vtesdle-today" -> vtesdleTodayScheduler.selectTodayVtesdle();
            case "proxy-card-options" -> proxyCardOptionScheduler.proxyCardOptionScheduler();
            case "i18n-images" -> i18nImageScheduler.scanImages();
            case "deck-index" -> deckIndex.refreshIndex();
            case "crypt-index" -> cryptCache.refreshIndex();
            case "library-index" -> libraryCache.refreshIndex();
            case "deck-archetypes" -> deckArchetypeScheduler.deckArchetypeScheduler();
            case "deck-archetype-index" -> deckArchetypeIndex.refreshIndex();
            case "user-month" -> userMonthScheduler.selectUsersOfMonth();
            case "achievements" -> achievementScheduler.reconcile();
            case "patreon-supporters" -> patreonSupporterScheduler.reconcile();
            case "patreon-reminder" -> patreonReminderScheduler.remindPatreon();
            default -> {
                return false;
            }
        }
        log.info("Manual scheduler completed actorUserId={} scheduler={}", actorUserId, key);
        return true;
    }
}
