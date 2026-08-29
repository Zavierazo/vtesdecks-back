package com.vtesdecks.scheduler.tournament;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TournamentEternalVigilanceDeckSchedulerTest {

    @Test
    public void shouldParseRoundsFormat() {
        assertEquals(2, TournamentEternalVigilanceDeckScheduler.getRounds("2R+F"));
        assertEquals(3, TournamentEternalVigilanceDeckScheduler.getRounds("3R+F"));
        assertEquals(4, TournamentEternalVigilanceDeckScheduler.getRounds(" 4 r + f "));
        assertEquals(3, TournamentEternalVigilanceDeckScheduler.getRounds("3R"));
    }

    @Test
    public void shouldIgnoreUnknownRoundsFormat() {
        assertNull(TournamentEternalVigilanceDeckScheduler.getRounds(null));
        assertNull(TournamentEternalVigilanceDeckScheduler.getRounds(""));
        assertNull(TournamentEternalVigilanceDeckScheduler.getRounds("Final only"));
        assertNull(TournamentEternalVigilanceDeckScheduler.getRounds("0R+F"));
    }
}
