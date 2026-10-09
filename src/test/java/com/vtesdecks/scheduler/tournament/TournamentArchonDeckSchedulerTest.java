package com.vtesdecks.scheduler.tournament;

import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.indexable.Crypt;
import com.vtesdecks.cache.indexable.Library;
import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.integration.ArchonClient;
import com.vtesdecks.jpa.entity.DeckEntity;
import com.vtesdecks.jpa.entity.TournamentSchedulerOwner;
import com.vtesdecks.jpa.entity.ArchonUserEntity;
import com.vtesdecks.jpa.repositories.ArchonUserRepository;
import com.vtesdecks.jpa.repositories.DeckCardRepository;
import com.vtesdecks.jpa.repositories.DeckRepository;
import com.vtesdecks.model.archon.ArchonDeck;
import com.vtesdecks.model.archon.ArchonTournament;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TournamentArchonDeckSchedulerTest {
    private static final int CRYPT_ID = 200001;
    private static final int LIBRARY_ID = 100001;

    @Mock
    private DeckRepository deckRepository;
    @Mock
    private DeckCardRepository deckCardRepository;
    @Mock
    private CryptCache cryptCache;
    @Mock
    private LibraryCache libraryCache;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private ArchonClient archonClient;
    @Mock
    private ArchonUserRepository archonUserRepository;
    @InjectMocks
    private TournamentArchonDeckScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler.setUp();
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(deckRepository.findById(any())).thenReturn(Optional.empty());
        when(deckCardRepository.findByIdDeckId(any())).thenReturn(List.of());
        when(cryptCache.get(any())).thenReturn(new Crypt());
        when(libraryCache.get(any())).thenReturn(new Library());
    }

    @Test
    void ranksFinalistsByVpThenTpThenSeatAndKeepsTheirSeats() {
        ArchonTournament tournament = tournament();
        tournament.setWinner("winner");
        tournament.getFinals().setSeating(List.of(
                seat("vp1", "1.0", 48),
                seat("tie-later-seat", "0.5", 24),
                seat("tie-earlier-seat", "0.5", 24),
                seat("winner", "0.0", 0),
                seat("vp2", "2.0", 12)
        ));

        List<TournamentArchonDeckScheduler.Finalist> finalists = scheduler.finalists(tournament);

        assertEquals(List.of("winner", "vp2", "vp1", "tie-later-seat", "tie-earlier-seat"),
                finalists.stream().map(TournamentArchonDeckScheduler.Finalist::userUid).toList());
        assertEquals(List.of(1, 2, 3, 4, 5), finalists.stream()
                .map(TournamentArchonDeckScheduler.Finalist::position).toList());
        assertEquals(4, finalists.getFirst().seat());
        assertEquals(2, finalists.get(3).seat());
        assertEquals(3, finalists.get(4).seat());
    }

    @Test
    void importsOnlyTheWinnerWhenFinalSeatingIsUnavailable() {
        ArchonTournament tournament = tournament();
        tournament.setFinals(null);

        List<TournamentArchonDeckScheduler.Finalist> finalists = scheduler.finalists(tournament);

        assertEquals(1, finalists.size());
        assertEquals("winner", finalists.getFirst().userUid());
        assertEquals(1, finalists.getFirst().position());
        assertEquals(null, finalists.getFirst().seat());
        assertEquals(null, finalists.getFirst().vp());
        assertEquals(null, finalists.getFirst().tp());
    }

    @Test
    void readsGzippedNdjsonExportWhenAStringContainsAUnicodeLineSeparator() throws Exception {
        String unicodeLineSeparator = String.valueOf((char) 0x2028);
        String response = "{\"type\":\"tournament\",\"data\":{\"uid\":\"first\",\"name\":\"First" + unicodeLineSeparator + "Tournament\"}}\n"
                + "{\"type\":\"tournament\",\"data\":{\"uid\":\"second\",\"name\":\"Second Tournament\"}}";
        Path export = Files.createTempFile("archon-export-test-", ".jsonl.gz");
        try {
            try (GZIPOutputStream gzip = new GZIPOutputStream(Files.newOutputStream(export));
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(gzip, StandardCharsets.UTF_8))) {
                writer.write(response);
            }

            List<ArchonTournament> tournaments = scheduler.readTournaments(export);

            assertEquals(2, tournaments.size());
            assertEquals("First" + unicodeLineSeparator + "Tournament", tournaments.getFirst().getName());
            assertEquals("second", tournaments.get(1).getUid());
        } finally {
            Files.deleteIfExists(export);
        }
    }

    @Test
    void skipsTournamentsOlderThanOneYear() {
        ArchonTournament tournament = tournament();
        tournament.setStart(LocalDateTime.now().minusYears(1).minusDays(1));

        assertFalse(scheduler.isFinishedFinal(tournament));
    }

    @Test
    void mapsArchonDeckUsingEventCodeAndVeknUrlWhenAvailable() {
        ArchonTournament tournament = tournament();
        TournamentArchonDeckScheduler.Finalist finalist = scheduler.finalists(tournament).getFirst();

        scheduler.parseDeck(tournament, finalist, deck(), Map.of("winner", "3120065"));

        ArgumentCaptor<DeckEntity> captor = ArgumentCaptor.forClass(DeckEntity.class);
        verify(deckRepository).saveAndFlush(captor.capture());
        DeckEntity mapped = captor.getValue();
        assertEquals("tournament-13370", mapped.getId());
        assertEquals(DeckType.TOURNAMENT, mapped.getType());
        assertEquals("Archon Event", mapped.getTournament());
        assertEquals(2, mapped.getPlayers());
        assertEquals(3, mapped.getRounds());
        assertEquals("Madrid, Spain", mapped.getPlace());
        assertEquals("Spain", mapped.getCountry());
        assertEquals(2026, mapped.getYear());
        assertEquals("3120065", mapped.getAuthor());
        assertEquals("13370", mapped.getEventId());
        assertEquals("https://www.vekn.net/event-calendar/event/13370", mapped.getUrl());
        assertEquals("https://archon.vekn.net/tournaments/tournament-uuid", mapped.getSource());
        assertEquals(new BigDecimal("1.5"), mapped.getFinalVp());
        assertEquals(1, mapped.getPosition());
        assertEquals(1, mapped.getFinalSeat());
        assertEquals(LocalDateTime.of(2026, 10, 3, 0, 0), mapped.getCreationDate());
    }

    @Test
    void resolvesMemberNameForNewDeck() {
        ArchonUserEntity member = new ArchonUserEntity();
        member.setName("Hernando Sagardia");
        when(archonUserRepository.findById("3120065")).thenReturn(Optional.of(member));
        ArchonTournament tournament = tournament();
        scheduler.parseDeck(tournament, scheduler.finalists(tournament).getFirst(), deck(), Map.of("winner", "3120065"));
        ArgumentCaptor<DeckEntity> captor = ArgumentCaptor.forClass(DeckEntity.class);
        verify(deckRepository).saveAndFlush(captor.capture());
        assertEquals("Hernando Sagardia", captor.getValue().getAuthor());
    }

    @Test
    void resolvesExistingArchonAuthorOnReimport() {
        DeckEntity actual = DeckEntity.builder().id("tournament-13370")
                .source("https://archon.vekn.net/tournaments/tournament-uuid")
                .type(DeckType.TOURNAMENT).schedulerOwner(TournamentSchedulerOwner.ARCHON)
                .author("3120065").verified(false).views(0L).build();
        when(deckRepository.findById(actual.getId())).thenReturn(Optional.of(actual));
        ArchonUserEntity member = new ArchonUserEntity();
        member.setVeknId("3120065");
        member.setName("Hernando Sagardia");
        when(archonUserRepository.findById("3120065")).thenReturn(Optional.of(member));
        ArchonTournament tournament = tournament();
        scheduler.parseDeck(tournament, scheduler.finalists(tournament).getFirst(), deck(), Map.of("winner", "3120065"));
        ArgumentCaptor<DeckEntity> captor = ArgumentCaptor.forClass(DeckEntity.class);
        verify(deckRepository).saveAndFlush(captor.capture());
        assertEquals("Hernando Sagardia", captor.getValue().getAuthor());
    }

    @Test
    void combinesArchonVenueCityAndCountryIntoThePlace() {
        ArchonTournament tournament = tournament();
        tournament.setVenue("La Tienda Scum");
        tournament.setCity("Barcelona");
        TournamentArchonDeckScheduler.Finalist finalist = scheduler.finalists(tournament).getFirst();

        scheduler.parseDeck(tournament, finalist, deck(), Map.of("winner", "3120065"));

        ArgumentCaptor<DeckEntity> captor = ArgumentCaptor.forClass(DeckEntity.class);
        verify(deckRepository).saveAndFlush(captor.capture());
        assertEquals("La Tienda Scum, Barcelona, Spain", captor.getValue().getPlace());
    }

    @Test
    void usesTournamentNameWhenArchonDeckNameIsACardSection() {
        ArchonTournament tournament = tournament();
        TournamentArchonDeckScheduler.Finalist finalist = scheduler.finalists(tournament).getFirst();
        ArchonDeck deck = deck();
        deck.setName("Crypt (12 cards, min=17 max=31 avg=6.25)");

        scheduler.parseDeck(tournament, finalist, deck, Map.of("winner", "3120065"));

        ArgumentCaptor<DeckEntity> captor = ArgumentCaptor.forClass(DeckEntity.class);
        verify(deckRepository).saveAndFlush(captor.capture());
        assertEquals("Archon Event", captor.getValue().getName());
    }

    @Test
    void mapsArchonDeckUsingArchonUrlWhenVeknExternalIdIsUnavailable() {
        ArchonTournament tournament = tournament();
        tournament.setEventCode("3A721K");
        tournament.setExternalIds(null);
        TournamentArchonDeckScheduler.Finalist finalist = scheduler.finalists(tournament).getFirst();

        scheduler.parseDeck(tournament, finalist, deck(), Map.of("winner", "3120065"));

        ArgumentCaptor<DeckEntity> captor = ArgumentCaptor.forClass(DeckEntity.class);
        verify(deckRepository).saveAndFlush(captor.capture());
        DeckEntity mapped = captor.getValue();
        assertEquals("tournament-3A721K", mapped.getId());
        assertEquals("3A721K", mapped.getEventId());
        assertEquals("https://archon.vekn.net/tournaments/tournament-uuid", mapped.getUrl());
    }

    @Test
    void usesTheFinalPositionInNonWinningDeckIds() {
        ArchonTournament tournament = tournament();
        TournamentArchonDeckScheduler.Finalist finalist = scheduler.finalists(tournament).get(1);
        ArchonDeck deck = deck();
        deck.setUserUid(finalist.userUid());

        scheduler.parseDeck(tournament, finalist, deck, Map.of());

        ArgumentCaptor<DeckEntity> captor = ArgumentCaptor.forClass(DeckEntity.class);
        verify(deckRepository).saveAndFlush(captor.capture());
        assertEquals(2, captor.getValue().getPosition());
        assertEquals("tournament-13370-2", captor.getValue().getId());
    }

    @Test
    void skipsDeckWithoutEventCode() {
        ArchonTournament tournament = tournament();
        tournament.setEventCode(null);

        scheduler.parseDeck(tournament, scheduler.finalists(tournament).getFirst(), deck(), Map.of());

        verify(deckRepository, never()).saveAndFlush(any());
    }

    @Test
    void skipsActiveDuplicateTournamentResult() {
        when(deckRepository.existsByTypeAndEventIdAndPositionAndIdNotAndDeletedFalse(
                DeckType.TOURNAMENT, "13370", 1, "tournament-13370")).thenReturn(true);

        ArchonTournament tournament = tournament();
        scheduler.parseDeck(tournament, scheduler.finalists(tournament).getFirst(), deck(), Map.of());

        verify(deckRepository, never()).saveAndFlush(any());
    }

    @Test
    void updatesOnlyTheFinalResultOfAnUnverifiedTwdaDeck() {
        DeckEntity actual = DeckEntity.builder()
                .id("tournament-13370")
                .source("http://www.vekn.fr/decks/twd.htm#deck-uuid")
                .type(DeckType.TOURNAMENT).schedulerOwner(TournamentSchedulerOwner.TWDA)
                .verified(false)
                .build();
        when(deckRepository.findById("tournament-13370")).thenReturn(Optional.of(actual));

        ArchonTournament tournament = tournament();
        scheduler.parseDeck(tournament, scheduler.finalists(tournament).getFirst(), deck(), Map.of());

        ArgumentCaptor<DeckEntity> captor = ArgumentCaptor.forClass(DeckEntity.class);
        verify(deckRepository).saveAndFlush(captor.capture());
        assertEquals(actual, captor.getValue());
        assertEquals(new BigDecimal("1.5"), actual.getFinalVp());
        assertEquals(1, actual.getFinalSeat());
        verify(deckCardRepository, never()).saveAndFlush(any());
    }

    @Test
    void neverUpdatesTheFinalResultOfAVerifiedTwdaDeck() {
        DeckEntity actual = DeckEntity.builder()
                .id("tournament-13370")
                .source("http://www.vekn.fr/decks/twd.htm#deck-uuid")
                .type(DeckType.TOURNAMENT).schedulerOwner(TournamentSchedulerOwner.TWDA)
                .verified(true)
                .build();
        when(deckRepository.findById("tournament-13370")).thenReturn(Optional.of(actual));

        ArchonTournament tournament = tournament();
        scheduler.parseDeck(tournament, scheduler.finalists(tournament).getFirst(), deck(), Map.of());

        verify(deckRepository, never()).saveAndFlush(any());
    }

    @Test
    void takesOverEternalVigilanceDeckUnderItsExistingId() {
        DeckEntity actual = DeckEntity.builder().id("legacy-id").type(DeckType.TOURNAMENT)
                .eventId("13370").position(1).schedulerOwner(TournamentSchedulerOwner.ETERNAL_VIGILANCE)
                .author("Old author").views(123L).customTags(List.of("keep"))
                .modificationDate(LocalDateTime.now().minusYears(1)).build();
        when(deckRepository.findByTypeAndEventIdAndPositionAndDeletedFalse(DeckType.TOURNAMENT, "13370", 1))
                .thenReturn(List.of(actual));
        ArchonTournament tournament = tournament();
        scheduler.parseDeck(tournament, scheduler.finalists(tournament).getFirst(), deck(), Map.of("winner", "3120065"));

        ArgumentCaptor<DeckEntity> captor = ArgumentCaptor.forClass(DeckEntity.class);
        verify(deckRepository).saveAndFlush(captor.capture());
        DeckEntity saved = captor.getValue();
        assertEquals("legacy-id", saved.getId());
        assertEquals(TournamentSchedulerOwner.ARCHON, saved.getSchedulerOwner());
        assertEquals("https://archon.vekn.net/tournaments/tournament-uuid", saved.getSource());
        assertEquals("3120065", saved.getAuthor());
        assertEquals(123L, saved.getViews());
        assertEquals(actual.getCustomTags(), saved.getCustomTags());
        assertFalse(saved.getVerified());
        var cards = ArgumentCaptor.forClass(com.vtesdecks.jpa.entity.DeckCardEntity.class);
        verify(deckCardRepository, org.mockito.Mockito.times(2)).saveAndFlush(cards.capture());
        org.junit.jupiter.api.Assertions.assertTrue(cards.getAllValues().stream()
                .allMatch(card -> "legacy-id".equals(card.getId().getDeckId())));
    }

    @Test
    void enrichesTwdaUnderDifferentIdWithoutChangingAnyOtherField() {
        DeckEntity actual = DeckEntity.builder().id("twda-legacy-id").type(DeckType.TOURNAMENT)
                .eventId("13370").position(1).schedulerOwner(TournamentSchedulerOwner.TWDA)
                .name("TWDA name").source("twda-source").author("Curated author").views(42L).build();
        DeckEntity expected = actual.toBuilder().finalVp(new BigDecimal("1.5")).finalSeat(1).build();
        when(deckRepository.findByTypeAndEventIdAndPositionAndDeletedFalse(DeckType.TOURNAMENT, "13370", 1))
                .thenReturn(List.of(actual));
        ArchonTournament tournament = tournament();
        scheduler.parseDeck(tournament, scheduler.finalists(tournament).getFirst(), deck(), Map.of());
        ArgumentCaptor<DeckEntity> captor = ArgumentCaptor.forClass(DeckEntity.class);
        verify(deckRepository).saveAndFlush(captor.capture());
        assertEquals(expected, captor.getValue());
        org.mockito.Mockito.verifyNoInteractions(deckCardRepository, archonUserRepository);
    }

    @Test
    void absentFinalResultsDoNotClearTwdaValues() {
        DeckEntity actual = DeckEntity.builder().id("tournament-13370").type(DeckType.TOURNAMENT)
                .schedulerOwner(TournamentSchedulerOwner.TWDA).finalVp(new BigDecimal("2.0")).finalSeat(5).build();
        when(deckRepository.findById(actual.getId())).thenReturn(Optional.of(actual));
        ArchonTournament tournament = tournament();
        scheduler.parseDeck(tournament, new TournamentArchonDeckScheduler.Finalist("winner", null, null, null, 1), deck(), Map.of());
        assertEquals(new BigDecimal("2.0"), actual.getFinalVp());
        assertEquals(5, actual.getFinalSeat());
        verify(deckRepository, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @CsvSource({"2,0,2", "2,3,2", "2,,2", "4,0,3", "0,2,2", "0,5,3", "0,0,", "0,-1,", "0,,", "-1,2,2", "-1,0,"})
    void mapsActualPreliminaryRoundsBeforeFallingBackToMaxRounds(int roundCount, Integer maxRounds, Integer expected) {
        ArchonTournament tournament = tournament();
        tournament.setMaxRounds(maxRounds);
        if (roundCount < 0) {
            tournament.setRounds(null);
        } else {
            tournament.setRounds(new ArrayList<>());
            for (int round = 0; round < roundCount; round++) {
                tournament.getRounds().add(List.of(new ArchonTournament.RoundTable(), new ArchonTournament.RoundTable()));
            }
        }
        scheduler.parseDeck(tournament, scheduler.finalists(tournament).getFirst(), deck(), Map.of());
        ArgumentCaptor<DeckEntity> captor = ArgumentCaptor.forClass(DeckEntity.class);
        verify(deckRepository).saveAndFlush(captor.capture());
        assertEquals(expected, captor.getValue().getRounds());
    }

    @Test
    void readsRoundArraysFromExportWithoutCountingTablesOrFinalsAsRounds() throws Exception {
        String response = """
                {"type":"tournament","data":{"uid":"019f1a1a-dff0-7323-9a8b-4f7a2e6b39cb","max_rounds":0,"rounds":[[{"state":"Finished","seating":[]},{"state":"Finished"}],[{"state":"Finished"}]],"finals":{"state":"Finished","seating":[]}}}
                """;
        Path export = Files.createTempFile("archon-rounds-test-", ".jsonl.gz");
        try {
            try (GZIPOutputStream gzip = new GZIPOutputStream(Files.newOutputStream(export))) {
                gzip.write(response.getBytes(StandardCharsets.UTF_8));
            }
            ArchonTournament parsed = scheduler.readTournaments(export).getFirst();
            assertEquals(0, parsed.getMaxRounds());
            assertEquals(2, parsed.getRounds().size());
            assertEquals(2, parsed.getRounds().getFirst().size());
            assertEquals("Finished", parsed.getFinals().getState());
        } finally {
            Files.deleteIfExists(export);
        }
    }

    private ArchonTournament tournament() {
        ArchonTournament tournament = new ArchonTournament();
        tournament.setUid("tournament-uuid");
        tournament.setName("Archon Event");
        tournament.setState("Finished");
        tournament.setStart(LocalDateTime.of(2026, 10, 3, 10, 0));
        tournament.setCountry("ES");
        tournament.setCity("Madrid");
        tournament.setMaxRounds(3);
        tournament.setWinner("winner");
        tournament.setDecklistsMode("Finalists");
        tournament.setEventCode("13370");
        ArchonTournament.ExternalIds externalIds = new ArchonTournament.ExternalIds();
        externalIds.setVekn("13370");
        tournament.setExternalIds(externalIds);
        ArchonTournament.Player competing = new ArchonTournament.Player();
        competing.setNonCompeting(false);
        ArchonTournament.Player secondCompeting = new ArchonTournament.Player();
        secondCompeting.setNonCompeting(false);
        ArchonTournament.Player proxy = new ArchonTournament.Player();
        proxy.setNonCompeting(true);
        tournament.setPlayers(List.of(competing, secondCompeting, proxy));
        ArchonTournament.Finals finals = new ArchonTournament.Finals();
        finals.setState("Finished");
        finals.setSeating(List.of(
                seat("winner", "1.5", 60),
                seat("second", "0.5", 24),
                seat("third", "0.5", 24),
                seat("fourth", "0.0", 0),
                seat("fifth", "0.0", 0)
        ));
        tournament.setFinals(finals);
        return tournament;
    }

    private ArchonTournament.Seat seat(String userUid, String vp, int tp) {
        ArchonTournament.Score score = new ArchonTournament.Score();
        score.setVp(new BigDecimal(vp));
        score.setTp(tp);
        ArchonTournament.Seat seat = new ArchonTournament.Seat();
        seat.setPlayerUid(userUid);
        seat.setResult(score);
        return seat;
    }

    private ArchonDeck deck() {
        ArchonDeck deck = new ArchonDeck();
        deck.setUid("deck-uuid");
        deck.setName("Winner Deck");
        deck.setComments("Deck comments");
        deck.setUserUid("winner");
        deck.setCards(Map.of(CRYPT_ID, 12, LIBRARY_ID, 60));
        return deck;
    }
}
