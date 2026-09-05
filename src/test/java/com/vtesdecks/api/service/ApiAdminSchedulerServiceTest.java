package com.vtesdecks.api.service;

import com.vtesdecks.cache.DeckArchetypeIndex;
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
        assertEquals(16, service.getAll().size());
        assertTrue(service.getAll().stream().anyMatch(item ->
                item.key().equals("deck-views-clean") && item.description().equals("Clean deck views")));
        assertTrue(service.getAll().stream().anyMatch(item ->
                item.key().equals("patreon-reminder") && item.description().equals("Send Patreon reminders")));
    }

    @Test
    void runsKnownScheduler() {
        assertTrue(service.run("achievements", 42));
        verify(achievementScheduler).reconcile();
    }

    @Test
    void rejectsUnknownScheduler() {
        assertFalse(service.run("unknown", 42));
        verifyNoInteractions(cleanUpScheduler, tournamentDeckScheduler, tournamentEternalVigilanceDeckScheduler,
                driveThruCardsScheduler, gamePodScheduler, vtesdleTodayScheduler, cardGameGeekScheduler,
                proxyCardOptionScheduler, marketScheduler, deckArchetypeScheduler, deckArchetypeIndex,
                userMonthScheduler, achievementScheduler, patreonReminderScheduler);
    }
}
