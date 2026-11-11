package com.vtesdecks.api.service;

import com.vtesdecks.cache.DeckArchetypeIndex;
import com.vtesdecks.cache.DeckIndex;
import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ApiAdminSchedulerServiceTest {
    @Mock
    private CleanUpScheduler cleanUpScheduler;
    @Mock
    private TournamentDeckScheduler tournamentDeckScheduler;
    @Mock
    private TournamentArchonDeckScheduler tournamentArchonDeckScheduler;
    @Mock
    private ArchonUserScheduler archonUserScheduler;
    @Mock
    private VtesdleTodayScheduler vtesdleTodayScheduler;
    @Mock
    private ProxyCardOptionScheduler proxyCardOptionScheduler;
    @Mock
    private I18nImageScheduler i18nImageScheduler;
    @Mock
    private DeckIndex deckIndex;
    @Mock
    private CryptCache cryptCache;
    @Mock
    private LibraryCache libraryCache;
    @Mock
    private MarketScheduler marketScheduler;
    @Mock
    private DeckArchetypeScheduler deckArchetypeScheduler;
    @Mock
    private DeckArchetypeIndex deckArchetypeIndex;
    @Mock
    private UserMonthScheduler userMonthScheduler;
    @Mock
    private AchievementScheduler achievementScheduler;
    @Mock
    private PatreonReminderScheduler patreonReminderScheduler;
    @Mock
    private PatreonSupporterScheduler patreonSupporterScheduler;
    @InjectMocks
    private ApiAdminSchedulerService service;

    @Test
    void exposesEveryMigratedManualScheduler() {
        assertEquals(23, service.getAll().size());
        assertFalse(service.getAll().stream().anyMatch(item -> item.key().equals("twda-eternal-vigilance")));
        assertFalse(service.getAll().stream().anyMatch(item -> item.key().equals("drive-thru-cards") || item.key().equals("game-pod")));
        assertFalse(service.getAll().stream().anyMatch(item -> item.key().equals("card-game-geek")));
        assertTrue(service.getAll().stream().anyMatch(item -> item.key().equals("market")));
        assertTrue(service.getAll().stream().anyMatch(item ->
                item.key().equals("collection-clean") && item.description().equals("Clean collections")));
        assertTrue(service.getAll().stream().anyMatch(item ->
                item.key().equals("deck-views-clean") && item.description().equals("Clean deck views")));
        assertTrue(service.getAll().stream().anyMatch(item ->
                item.key().equals("patreon-reminder") && item.description().equals("Send Patreon reminders")));
        assertTrue(service.getAll().stream().anyMatch(item -> item.key().equals("comments-clean")));
        assertTrue(service.getAll().stream().anyMatch(item -> item.key().equals("reactions-clean")));
        assertTrue(service.getAll().stream().anyMatch(item -> item.key().equals("notifications-clean")));
        assertTrue(service.getAll().stream().anyMatch(item -> item.key().equals("email-actions-clean")));
        assertTrue(service.getAll().stream().anyMatch(item -> item.key().equals("deck-index")
                && item.description().equals("Refresh deck index")));
        assertTrue(service.getAll().stream().anyMatch(item -> item.key().equals("crypt-index")
                && item.description().equals("Refresh crypt index")));
        assertTrue(service.getAll().stream().anyMatch(item -> item.key().equals("library-index")
                && item.description().equals("Refresh library index")));
        assertTrue(service.getAll().stream().anyMatch(item -> item.key().equals("twda-archon-decks")
                && item.description().equals("Import Archon finalist decks")));
    }

    @Test
    void runsPatreonSupporterReconciliation() {
        assertTrue(service.run("patreon-supporters", 42));
        verify(patreonSupporterScheduler).reconcile();
    }

    @Test
    void runsKnownScheduler() {
        assertTrue(service.run("achievements", 42));
        verify(achievementScheduler).reconcile();
    }

    @Test
    void runsMarketScheduler() {
        assertTrue(service.run("market", 42));
        verify(marketScheduler).scrapCards();
    }

    @Test
    void runsI18nImageDiscovery() {
        assertTrue(service.getAll().stream().anyMatch(item -> item.key().equals("i18n-images")));
        assertTrue(service.run("i18n-images", 42));
        verify(i18nImageScheduler).scanImages();
    }

    @Test
    void runsArchonScheduler() {
        assertTrue(service.run("twda-archon-decks", 42));
        verify(tournamentArchonDeckScheduler).scrappingDecks();
    }

    @Test
    void runsArchonMemberScheduler() {
        assertTrue(service.getAll().stream().anyMatch(item -> item.key().equals("archon-users")));
        assertTrue(service.run("archon-users", 42));
        verify(archonUserScheduler).scrappingUsers();
    }

    @Test
    void runsCollectionCleanup() {
        assertTrue(service.run("collection-clean", 42));
        verify(cleanUpScheduler).collectionCleanScheduler();
    }

    @Test
    void refreshesCardAndDeckIndexes() {
        assertTrue(service.run("deck-index", 42));
        assertTrue(service.run("crypt-index", 42));
        assertTrue(service.run("library-index", 42));

        verify(deckIndex).refreshIndex();
        verify(cryptCache).refreshIndex();
        verify(libraryCache).refreshIndex();
    }

    @Test
    void runsNewCleanupSchedulers() {
        assertTrue(service.run("comments-clean", 42));
        assertTrue(service.run("reactions-clean", 42));
        assertTrue(service.run("notifications-clean", 42));
        assertTrue(service.run("email-actions-clean", 42));
        verify(cleanUpScheduler).commentsCleanScheduler();
        verify(cleanUpScheduler).reactionsCleanScheduler();
        verify(cleanUpScheduler).notificationsCleanScheduler();
        verify(cleanUpScheduler).emailActionsCleanScheduler();
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "twda-eternal-vigilance", "drive-thru-cards", "game-pod", "card-game-geek"})
    void rejectsUnknownOrRetiredScheduler(String key) {
        assertFalse(service.run(key, 42));
        verifyNoInteractions(cleanUpScheduler, tournamentDeckScheduler,
                tournamentArchonDeckScheduler,
                vtesdleTodayScheduler,
                proxyCardOptionScheduler, i18nImageScheduler, marketScheduler, deckArchetypeScheduler, deckArchetypeIndex,
                deckIndex, cryptCache, libraryCache, userMonthScheduler, achievementScheduler, patreonReminderScheduler);
    }
}
