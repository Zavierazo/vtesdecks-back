package com.vtesdecks.scheduler.tournament;

import com.vtesdecks.scheduler.tournament.helpers.TournamentEventId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TournamentEventIdTest {

    @Test
    void extractsSupportedOfficialEventIds() {
        assertEquals("10643", TournamentEventId.fromUrl("https://www.vekn.net/event-calendar/event/10643"));
        assertEquals("13293", TournamentEventId.fromUrl("https://archon.vekn.net/t/13293"));
        assertEquals("0bbf344e-f63e-41bf-9b74-0fc82e91ae61",
                TournamentEventId.fromUrl("https://archon.vekn.net/tournament/0bbf344e-f63e-41bf-9b74-0fc82e91ae61/display.html"));
    }

    @Test
    void ignoresUnsupportedUrls() {
        assertNull(TournamentEventId.fromUrl(null));
        assertNull(TournamentEventId.fromUrl("https://example.org/event/10643"));
        assertNull(TournamentEventId.fromUrl("https://www.vekn.net/event-calendar/event/not-a-number"));
    }
}
