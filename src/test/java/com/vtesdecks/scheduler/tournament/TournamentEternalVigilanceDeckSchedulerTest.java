package com.vtesdecks.scheduler.tournament;

import org.junit.jupiter.api.Test;
import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.jpa.entity.DeckEntity;
import com.vtesdecks.jpa.repositories.DeckCardRepository;
import com.vtesdecks.jpa.repositories.DeckRepository;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class TournamentEternalVigilanceDeckSchedulerTest {

    private static final String EVENT_URL = "https://www.vekn.net/event-calendar/event/12345";

    @Test
    public void shouldSkipNewDeckWhenTournamentUrlAlreadyExists() throws Exception {
        assertImport(true, false, EVENT_URL, false);
    }

    @Test
    public void shouldImportNewTournamentUrl() throws Exception {
        assertImport(false, false, EVENT_URL, true);
    }

    @Test
    public void shouldStoreEventIdAndWinningPosition() throws Exception {
        DeckEntity deck = assertImport(false, false, EVENT_URL, true);

        assertEquals("12345", deck.getEventId());
        assertEquals(1, deck.getPosition());
        assertNull(deck.getFinalVp());
    }

    @Test
    public void shouldSkipDuplicateTournamentResult() throws Exception {
        assertImport(false, true, false, EVENT_URL, false);
    }

    @Test
    public void shouldUpdateExistingIdWithoutDuplicateUrlCheck() throws Exception {
        assertImport(false, true, EVENT_URL, true);
    }

    @Test
    public void shouldImportWithoutTournamentUrl() throws Exception {
        assertImport(false, false, null, true);
        assertImport(false, false, "   ", true);
    }

    @Test
    public void shouldSkipDuplicateByNameTournamentAndDateWithoutMatchingUrl() throws Exception {
        assertImport(false, false, EVENT_URL, false, matchingDeck());
        assertImport(false, false, null, false, matchingDeck());
    }

    @Test
    public void shouldAllowDifferentTournamentDateOrDeletedDeck() throws Exception {
        DeckEntity match = matchingDeck();
        assertImport(false, false, EVENT_URL, true, match.toBuilder().tournament("Other tournament").build());
        assertImport(false, false, EVENT_URL, true, match.toBuilder().tournament(null).build());
        assertImport(false, false, EVENT_URL, true,
                match.toBuilder().creationDate(match.getCreationDate().plusDays(1)).build());
        assertImport(false, false, EVENT_URL, true, match.toBuilder().deleted(true).build());
    }

    @Test
    public void shouldUpdateExistingIdEvenWhenMetadataMatchesAnotherDeck() throws Exception {
        assertImport(false, true, EVENT_URL, true, matchingDeck());
    }

    private DeckEntity matchingDeck() {
        return DeckEntity.builder().id("tournament-other-id").name("Example deck")
                .tournament("EXAMPLE TOURNAMENT").creationDate(LocalDateTime.of(2026, 9, 1, 18, 30)).build();
    }

    private DeckEntity assertImport(boolean duplicate, boolean existing, String eventUrl, boolean saved,
                                   DeckEntity... matchingDecks) throws Exception {
        return assertImport(duplicate, false, existing, eventUrl, saved, matchingDecks);
    }

    private DeckEntity assertImport(boolean duplicate, boolean duplicateTournamentResult, boolean existing, String eventUrl,
                                   boolean saved, DeckEntity... matchingDecks) throws Exception {
        DeckRepository decks = mock(DeckRepository.class);
        DeckCardRepository cards = mock(DeckCardRepository.class);
        TournamentEternalVigilanceDeckScheduler scheduler = new TournamentEternalVigilanceDeckScheduler(
                decks, cards, null, null, mock(PlatformTransactionManager.class));
        scheduler.setUp();
        when(decks.findById("tournament-12345")).thenReturn(existing
                ? Optional.of(DeckEntity.builder().id("tournament-12345").name("Old name").build())
                : Optional.empty());
        when(decks.existsByTypeAndUrlIgnoreCaseAndDeletedFalse(DeckType.TOURNAMENT, EVENT_URL))
                .thenReturn(duplicate);
        when(decks.existsByTypeAndEventIdAndPositionAndIdNotAndDeletedFalse(
                DeckType.TOURNAMENT, "12345", 1, "tournament-12345"))
                .thenReturn(duplicateTournamentResult);
        when(decks.findByTypeAndNameContainingIgnoreCase(DeckType.TOURNAMENT, "Example deck"))
                .thenReturn(List.of(matchingDecks));
        Connection connection = mock(Connection.class, RETURNS_SELF);
        Connection.Response response = mock(Connection.Response.class);
        when(connection.execute()).thenReturn(response);
        when(response.body()).thenReturn("""
                name: Example tournament
                date_start: 2026-09-01
                %s
                deck:
                  name: Example deck
                  crypt:
                    - id: 200001
                      count: 12
                  library_sections:
                    - cards:
                        - id: 100001
                          count: 60
                """.formatted(eventUrl == null ? "" : "event_url: '" + eventUrl + "'"));
        try (MockedStatic<Jsoup> jsoup = mockStatic(Jsoup.class)) {
            jsoup.when(() -> Jsoup.connect(anyString())).thenReturn(connection);
            ReflectionTestUtils.invokeMethod(scheduler, "parseDeck", "12345", "2026/09/12345.yaml");
        }
        if (saved) {
            var deckCaptor = org.mockito.ArgumentCaptor.forClass(DeckEntity.class);
            verify(decks).saveAndFlush(deckCaptor.capture());
            verify(cards, times(2)).saveAndFlush(any());
            if (eventUrl == null || eventUrl.isBlank()) {
                verify(decks, never()).existsByTypeAndEventIdAndPositionAndIdNotAndDeletedFalse(any(), any(), any(), any());
            }
            return deckCaptor.getValue();
        } else {
            verify(decks, never()).saveAndFlush(any());
            verifyNoInteractions(cards);
        }
        if (existing || eventUrl == null || eventUrl.isBlank()) {
            verify(decks, never()).existsByTypeAndUrlIgnoreCaseAndDeletedFalse(any(), any());
        } else {
            verify(decks).existsByTypeAndUrlIgnoreCaseAndDeletedFalse(DeckType.TOURNAMENT, EVENT_URL);
        }
        if (existing || duplicate || duplicateTournamentResult) {
            verify(decks, never()).findByTypeAndNameContainingIgnoreCase(any(), any());
        } else {
            verify(decks).findByTypeAndNameContainingIgnoreCase(DeckType.TOURNAMENT, "Example deck");
        }
        return null;
    }

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
