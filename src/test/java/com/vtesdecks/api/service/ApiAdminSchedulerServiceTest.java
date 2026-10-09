package com.vtesdecks.api.service;

import com.vtesdecks.cache.DeckArchetypeIndex;
import com.vtesdecks.cache.DeckIndex;
import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.scheduler.AchievementScheduler;
import com.vtesdecks.scheduler.ArchonUserScheduler;
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
import com.vtesdecks.scheduler.tournament.TournamentArchonDeckScheduler;
import com.vtesdecks.scheduler.tournament.TournamentEternalVigilanceDeckScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    private TournamentEternalVigilanceDeckScheduler tournamentEternalVigilanceDeckScheduler;
    @Mock
    private TournamentArchonDeckScheduler tournamentArchonDeckScheduler;
    @Mock
    private ArchonUserScheduler archonUserScheduler;
    @Mock
    private DriveThruCardsScheduler driveThruCardsScheduler;
    @Mock
    private GamePodScheduler gamePodScheduler;
    @Mock
    private VtesdleTodayScheduler vtesdleTodayScheduler;
    @Mock
    private CardGameGeekScheduler cardGameGeekScheduler;
    @Mock
    private ProxyCardOptionScheduler proxyCardOptionScheduler;
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
    @InjectMocks
    private ApiAdminSchedulerService service;

    @Test
    void exposesEveryMigratedManualScheduler() {
        assertEquals(25, service.getAll().size());
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
    void runsKnownScheduler() {
        assertTrue(service.run("achievements", 42));
        verify(achievementScheduler).reconcile();
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

    @Test
    void rejectsUnknownScheduler() {
        assertFalse(service.run("unknown", 42));
        verifyNoInteractions(cleanUpScheduler, tournamentDeckScheduler, tournamentEternalVigilanceDeckScheduler,
                tournamentArchonDeckScheduler,
                driveThruCardsScheduler, gamePodScheduler, vtesdleTodayScheduler, cardGameGeekScheduler,
                proxyCardOptionScheduler, marketScheduler, deckArchetypeScheduler, deckArchetypeIndex,
                deckIndex, cryptCache, libraryCache, userMonthScheduler, achievementScheduler, patreonReminderScheduler);
    }
}
