package com.vtesdecks.scheduler.tournament;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.indexable.Crypt;
import com.vtesdecks.cache.indexable.Library;
import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.integration.ArchonClient;
import com.vtesdecks.jpa.entity.DeckEntity;
import com.vtesdecks.jpa.entity.DeckCardEntity;
import com.vtesdecks.jpa.entity.TournamentSchedulerOwner;
import com.vtesdecks.jpa.entity.ArchonUserEntity;
import com.vtesdecks.jpa.repositories.ArchonUserRepository;
import com.vtesdecks.jpa.repositories.DeckCardRepository;
import com.vtesdecks.jpa.repositories.DeckRepository;
import com.vtesdecks.model.archon.ArchonDeck;
import com.vtesdecks.model.archon.ArchonAccessToken;
import com.vtesdecks.model.archon.ArchonTournament;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
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

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
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

    @AfterEach
    void doesNotFlushEachCardIndividually() {
        verify(deckCardRepository, never()).saveAndFlush(any());
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

    @ParameterizedTest
    @ValueSource(ints = {1995, 2000, 2023})
    void acceptsFinishedTournamentsFromAnyYear(int year) {
        ArchonTournament tournament = tournament();
        tournament.setStart(LocalDateTime.of(year, 1, 1, 0, 0));

        org.junit.jupiter.api.Assertions.assertTrue(scheduler.isFinishedTournament(tournament));
    }

    @ParameterizedTest
    @CsvSource({"TWDA,false", "TWDA,true", "ARCHON,false", "ARCHON,true", "ETERNAL_VIGILANCE,false", "ETERNAL_VIGILANCE,true"})
    void fillsHistoricalVeknIdsWithoutADecklist(TournamentSchedulerOwner owner, boolean verified) throws Exception {
        ArchonTournament tournament = tournament();
        tournament.setStart(LocalDateTime.of(2000, 1, 1, 0, 0));
        DeckEntity actual = DeckEntity.builder().id("historical-deck").type(DeckType.TOURNAMENT)
                .eventId("13370").position(1).schedulerOwner(owner).verified(verified)
                .name("Historical deck").author("Original author").source("original-source")
                .finalVp(new BigDecimal("1.5")).finalSeat(1).creationDate(tournament.getStart()).build();
        DeckEntity expected = actual.toBuilder().veknId("003120065").build();
        when(deckRepository.findByTypeAndEventIdAndPositionAndDeletedFalse(DeckType.TOURNAMENT, "13370", 1))
                .thenReturn(List.of(actual));

        importExport(tournament, List.of(), Map.of("winner", "003120065"));

        verify(deckRepository).saveAndFlush(actual);
        assertEquals(expected, actual);
        org.mockito.Mockito.verifyNoInteractions(deckCardRepository, archonUserRepository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"None", "Private"})
    void fillsHistoricalVeknIdWithoutImportingUnpublishedDecklists(String mode) throws Exception {
        ArchonTournament tournament = tournament();
        tournament.setStart(LocalDateTime.of(2000, 1, 1, 0, 0));
        tournament.setDecklistsMode(mode);
        DeckEntity actual = DeckEntity.builder().id("historical-deck").type(DeckType.TOURNAMENT)
                .eventId("13370").position(1).schedulerOwner(TournamentSchedulerOwner.ARCHON)
                .name("Historical deck").author("Original author").source("original-source")
                .finalVp(new BigDecimal("1.5")).finalSeat(1).build();
        DeckEntity expected = actual.toBuilder().veknId("003120065").build();
        when(deckRepository.findByTypeAndEventIdAndPositionAndDeletedFalse(DeckType.TOURNAMENT, "13370", 1))
                .thenReturn(List.of(actual));

        importExport(tournament, List.of(deck()), Map.of("winner", "003120065"));

        verify(deckRepository).saveAndFlush(actual);
        assertEquals(expected, actual);
        org.mockito.Mockito.verifyNoInteractions(deckCardRepository, archonUserRepository);
    }

    @Test
    void fillsHistoricalVeknIdsForEveryFinalistWithoutDecklists() throws Exception {
        ArchonTournament tournament = tournament();
        tournament.setStart(LocalDateTime.of(1998, 1, 1, 0, 0));
        List<DeckEntity> decks = new ArrayList<>();
        Map<String, String> veknIds = new java.util.HashMap<>();
        for (TournamentArchonDeckScheduler.Finalist finalist : scheduler.finalists(tournament)) {
            DeckEntity actual = DeckEntity.builder().id("historical-deck-" + finalist.position()).type(DeckType.TOURNAMENT)
                    .eventId("13370").position(finalist.position()).schedulerOwner(TournamentSchedulerOwner.TWDA)
                    .verified(true).author("Original author").build();
            when(deckRepository.findByTypeAndEventIdAndPositionAndDeletedFalse(DeckType.TOURNAMENT, "13370", finalist.position()))
                    .thenReturn(List.of(actual));
            decks.add(actual);
            veknIds.put(finalist.userUid(), "000000" + finalist.position());
        }

        importExport(tournament, List.of(), veknIds);

        verify(deckRepository, org.mockito.Mockito.times(5)).saveAndFlush(any());
        for (DeckEntity actual : decks) {
            assertEquals("000000" + actual.getPosition(), actual.getVeknId());
            assertEquals("Original author", actual.getAuthor());
            org.junit.jupiter.api.Assertions.assertNull(actual.getFinalVp());
            org.junit.jupiter.api.Assertions.assertNull(actual.getFinalSeat());
        }
        org.mockito.Mockito.verifyNoInteractions(deckCardRepository, archonUserRepository);
    }

    @Test
    void importsPublishedDecklistsFromHistoricalTournaments() throws Exception {
        ArchonTournament tournament = tournament();
        tournament.setStart(LocalDateTime.of(1998, 1, 1, 0, 0));

        importExport(tournament, List.of(deck()), Map.of("winner", "003120065"));

        ArgumentCaptor<DeckEntity> captor = ArgumentCaptor.forClass(DeckEntity.class);
        verify(deckRepository).saveAndFlush(captor.capture());
        assertEquals(1998, captor.getValue().getYear());
        assertEquals(tournament.getStart(), captor.getValue().getCreationDate());
        assertEquals("003120065", captor.getValue().getVeknId());
        verify(deckCardRepository, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    void doesNotCreateHistoricalDecksWithoutDecklists() throws Exception {
        ArchonTournament tournament = tournament();
        tournament.setStart(LocalDateTime.of(1998, 1, 1, 0, 0));

        importExport(tournament, List.of(), Map.of("winner", "003120065"));

        verify(deckRepository, never()).saveAndFlush(any());
        org.mockito.Mockito.verifyNoInteractions(deckCardRepository, archonUserRepository);
    }

    @Test
    void doesNotUpdateHistoricalIdsWhenTournamentIdentityIsAmbiguous() throws Exception {
        ArchonTournament tournament = tournament();
        tournament.setStart(LocalDateTime.of(1998, 1, 1, 0, 0));
        DeckEntity first = DeckEntity.builder().id("first").type(DeckType.TOURNAMENT)
                .eventId("13370").position(1).schedulerOwner(TournamentSchedulerOwner.TWDA).verified(true).build();
        when(deckRepository.findByTypeAndEventIdAndPositionAndDeletedFalse(DeckType.TOURNAMENT, "13370", 1))
                .thenReturn(List.of(first, first.toBuilder().id("second").build()));

        importExport(tournament, List.of(), Map.of("winner", "003120065"));

        verify(deckRepository, never()).saveAndFlush(any());
        org.junit.jupiter.api.Assertions.assertNull(first.getVeknId());
    }

    @Test
    void waitsForHistoricalTournamentsToFinishBeforeLinkingPlayers() throws Exception {
        ArchonTournament tournament = tournament();
        tournament.setStart(LocalDateTime.of(1998, 1, 1, 0, 0));
        tournament.setState("Playing");

        importExport(tournament, List.of(deck()), Map.of("winner", "003120065"));

        org.mockito.Mockito.verifyNoInteractions(deckRepository, deckCardRepository, archonUserRepository);
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
        assertEquals("3120065", mapped.getVeknId());
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
        assertEquals("3120065", captor.getValue().getVeknId());
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
        assertEquals("3120065", captor.getValue().getVeknId());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "different-id", "003120065"})
    void preservesExistingVeknIdOnArchonReimport(String incomingId) {
        DeckEntity actual = DeckEntity.builder().id("tournament-13370").type(DeckType.TOURNAMENT)
                .eventId("13370").position(1).schedulerOwner(TournamentSchedulerOwner.ARCHON)
                .veknId("003120065").build();
        when(deckRepository.findById(actual.getId())).thenReturn(Optional.of(actual));
        ArchonTournament tournament = tournament();

        scheduler.parseDeck(tournament, scheduler.finalists(tournament).getFirst(), deck(),
                incomingId == null ? Map.of() : Map.of("winner", incomingId));

        ArgumentCaptor<DeckEntity> captor = ArgumentCaptor.forClass(DeckEntity.class);
        verify(deckRepository).saveAndFlush(captor.capture());
        assertEquals("003120065", captor.getValue().getVeknId());
    }

    @Test
    void reportsConflictingVeknIdsFromArchonWithoutChangingVerifiedDecks(CapturedOutput output) {
        DeckEntity actual = DeckEntity.builder().id("tournament-13370").type(DeckType.TOURNAMENT)
                .schedulerOwner(TournamentSchedulerOwner.ARCHON).verified(true).veknId("original-id").build();
        when(deckRepository.findById(actual.getId())).thenReturn(Optional.of(actual));
        ArchonTournament tournament = tournament();

        scheduler.parseDeck(tournament, scheduler.finalists(tournament).getFirst(), deck(), Map.of("winner", "different-id"));

        assertEquals("original-id", actual.getVeknId());
        org.junit.jupiter.api.Assertions.assertTrue(output.getAll()
                .contains("Preserving VEKN ID original-id for deck tournament-13370, Archon supplied different-id"));
        verify(deckRepository, never()).saveAndFlush(any());
    }

    @Test
    void normalizesVeknIdBeforeResolvingAuthor() {
        ArchonTournament tournament = tournament();
        ArchonUserEntity member = new ArchonUserEntity();
        member.setName("Tournament player");
        when(archonUserRepository.findById("003120065")).thenReturn(Optional.of(member));

        scheduler.parseDeck(tournament, scheduler.finalists(tournament).getFirst(), deck(), Map.of("winner", " 003120065 "));

        ArgumentCaptor<DeckEntity> captor = ArgumentCaptor.forClass(DeckEntity.class);
        verify(deckRepository).saveAndFlush(captor.capture());
        assertEquals("003120065", captor.getValue().getVeknId());
        assertEquals("Tournament player", captor.getValue().getAuthor());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void skipsNewDeckWithAWarningWhenArchonDoesNotSupplyVeknId(String veknId, CapturedOutput output) {
        ArchonTournament tournament = tournament();

        scheduler.parseDeck(tournament, scheduler.finalists(tournament).getFirst(), deck(),
                veknId == null ? Map.of() : Map.of("winner", veknId));

        verify(deckRepository, never()).saveAndFlush(any());
        verify(deckCardRepository, never()).save(any());
        org.junit.jupiter.api.Assertions.assertTrue(output.getAll().contains("WARN"));
        org.junit.jupiter.api.Assertions.assertTrue(output.getAll().contains("eventId and veknId are required"));
        org.junit.jupiter.api.Assertions.assertTrue(output.getAll().contains("tournament-13370"));
        org.junit.jupiter.api.Assertions.assertTrue(output.getAll().contains("https://archon.vekn.net/tournaments/"));
    }

    @ParameterizedTest
    @EnumSource(TournamentSchedulerOwner.class)
    void fillsOnlyMissingVeknIdOfVerifiedDecksUnderTheirExistingId(TournamentSchedulerOwner owner) {
        DeckEntity actual = DeckEntity.builder().id("curated-id").type(DeckType.TOURNAMENT)
                .eventId("13370").position(1).schedulerOwner(owner).verified(true)
                .name("Curated deck").author("Curated author").source("curated-source")
                .finalVp(new BigDecimal("2.0")).finalSeat(5).views(42L).build();
        DeckEntity expected = actual.toBuilder().veknId("003120065").build();
        when(deckRepository.findByTypeAndEventIdAndPositionAndDeletedFalse(DeckType.TOURNAMENT, "13370", 1))
                .thenReturn(List.of(actual));
        ArchonTournament tournament = tournament();

        scheduler.parseDeck(tournament, scheduler.finalists(tournament).getFirst(), deck(), Map.of("winner", "003120065"));

        ArgumentCaptor<DeckEntity> captor = ArgumentCaptor.forClass(DeckEntity.class);
        verify(deckRepository).saveAndFlush(captor.capture());
        assertEquals(expected, captor.getValue());
        org.mockito.Mockito.verifyNoInteractions(deckCardRepository, archonUserRepository);

        org.mockito.Mockito.clearInvocations(deckRepository);
        scheduler.parseDeck(tournament, scheduler.finalists(tournament).getFirst(), deck(), Map.of("winner", "003120065"));
        scheduler.parseDeck(tournament, scheduler.finalists(tournament).getFirst(), deck(), Map.of("winner", "different-id"));
        verify(deckRepository, never()).saveAndFlush(any());
        assertEquals(expected, actual);
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

        scheduler.parseDeck(tournament, finalist, deck, Map.of(finalist.userUid(), "0012345"));

        ArgumentCaptor<DeckEntity> captor = ArgumentCaptor.forClass(DeckEntity.class);
        verify(deckRepository).saveAndFlush(captor.capture());
        assertEquals(2, captor.getValue().getPosition());
        assertEquals("tournament-13370-2", captor.getValue().getId());
        assertEquals("0012345", captor.getValue().getVeknId());
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
        when(deckRepository.findByTypeAndEventIdAndPositionAndDeletedFalse(DeckType.TOURNAMENT, "13370", 1))
                .thenReturn(List.of(), List.of(DeckEntity.builder().id("duplicate").type(DeckType.TOURNAMENT)
                        .eventId("13370").position(1).build()));

        ArchonTournament tournament = tournament();
        scheduler.parseDeck(tournament, scheduler.finalists(tournament).getFirst(), deck(), Map.of());

        verify(deckRepository, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @CsvSource({"false,", "true,", "false,''", "true,''", "false,'  '", "true,'  '"})
    void fillsOnlyMissingVeknIdWhenSkippingHistoricalDuplicate(boolean verified, String existingVeknId) {
        ArchonTournament tournament = tournament();
        DeckEntity existing = DeckEntity.builder().id("tournament-legacy").type(DeckType.TOURNAMENT)
                .position(1).name("Incorrect archived title").author(" ALEX--SMITH ")
                .tournament(" ÁRCHON   event ").creationDate(tournament.getStart())
                .schedulerOwner(TournamentSchedulerOwner.TWDA).verified(verified).veknId(existingVeknId)
                .description("Curated description").views(42L).source("original-source")
                .finalVp(new BigDecimal("2.0")).finalSeat(5).modificationDate(tournament.getStart()).build();
        DeckEntity before = existing.toBuilder().build();
        ArchonUserEntity member = new ArchonUserEntity();
        member.setName("Álex Smith");
        when(archonUserRepository.findById("003120065")).thenReturn(Optional.of(member));
        when(deckRepository.findActiveTournamentDecksBetween(tournament.getStart().toLocalDate().atStartOfDay(),
                tournament.getStart().toLocalDate().plusDays(1).atStartOfDay())).thenReturn(List.of(existing));

        scheduler.parseDeck(tournament, scheduler.finalists(tournament).getFirst(), deck(), Map.of("winner", "003120065"));

        verify(deckRepository).fillMissingVeknId("tournament-legacy", "003120065");
        assertEquals(before, existing);
        assertEquals(before.getModificationDate(), existing.getModificationDate());
        verify(deckRepository, never()).saveAndFlush(any());
        verify(deckCardRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"003120065", "0099999"})
    void preservesExistingVeknIdWhenSkippingHistoricalDuplicate(String existingVeknId) {
        ArchonTournament tournament = tournament();
        DeckEntity existing = DeckEntity.builder().id("historical").type(DeckType.TOURNAMENT)
                .position(1).veknId(existingVeknId).author("Álex Smith").tournament("Archon Event")
                .creationDate(tournament.getStart()).verified(true).build();
        ArchonUserEntity member = new ArchonUserEntity();
        member.setName("Álex Smith");
        when(archonUserRepository.findById("003120065")).thenReturn(Optional.of(member));
        when(deckRepository.findActiveTournamentDecksBetween(any(), any())).thenReturn(List.of(existing));

        scheduler.parseDeck(tournament, scheduler.finalists(tournament).getFirst(), deck(), Map.of("winner", "003120065"));

        assertEquals(existingVeknId, existing.getVeknId());
        verify(deckRepository, never()).fillMissingVeknId(any(), any());
        verify(deckRepository, never()).saveAndFlush(any());
        verify(deckCardRepository, never()).save(any());
    }

    @Test
    void doesNotFillVeknIdWhenHistoricalDuplicateIsAmbiguous() {
        ArchonTournament tournament = tournament();
        DeckEntity first = DeckEntity.builder().id("first").type(DeckType.TOURNAMENT)
                .position(1).author("Álex Smith").tournament("Archon Event").creationDate(tournament.getStart()).build();
        ArchonUserEntity member = new ArchonUserEntity();
        member.setName("Álex Smith");
        when(archonUserRepository.findById("003120065")).thenReturn(Optional.of(member));
        when(deckRepository.findActiveTournamentDecksBetween(any(), any()))
                .thenReturn(List.of(first, first.toBuilder().id("second").build()));

        scheduler.parseDeck(tournament, scheduler.finalists(tournament).getFirst(), deck(), Map.of("winner", "003120065"));

        verify(deckRepository, never()).fillMissingVeknId(any(), any());
        verify(deckRepository, never()).saveAndFlush(any());
        verify(deckCardRepository, never()).save(any());
    }

    @Test
    void skipsDuplicateWithoutWritingWhenArchonHasNoVeknId() {
        ArchonTournament tournament = tournament();
        DeckEntity existing = DeckEntity.builder().id("historical").type(DeckType.TOURNAMENT)
                .eventId("13370").position(null).creationDate(tournament.getStart()).build();
        when(deckRepository.findActiveTournamentDecksBetween(any(), any())).thenReturn(List.of(existing));

        scheduler.parseDeck(tournament, scheduler.finalists(tournament).getFirst(), deck(), Map.of());

        verify(deckRepository, never()).fillMissingVeknId(any(), any());
        verify(deckRepository, never()).saveAndFlush(any());
        verify(deckCardRepository, never()).save(any());
    }

    @ParameterizedTest
    @CsvSource({"TWDA,false", "TWDA,true", "ARCHON,false", "ARCHON,true", "ETERNAL_VIGILANCE,false", "ETERNAL_VIGILANCE,true"})
    void fillsOnlyVeknIdForUniqueCompositionMatchDespiteDifferentEventMetadata(TournamentSchedulerOwner owner, boolean verified) {
        DeckEntity existing = compositionCandidate("legacy").toBuilder().schedulerOwner(owner).verified(verified).build();
        DeckEntity before = existing.toBuilder().build();
        prepareCompositionMatches(List.of(existing));

        ArchonTournament tournament = tournament();
        scheduler.parseDeck(tournament, scheduler.finalists(tournament).getFirst(), deck(), Map.of("winner", "003120065"));

        verify(deckRepository).fillMissingVeknId("legacy", "003120065");
        assertEquals(before, existing);
        assertEquals(before.getEventId(), existing.getEventId());
        assertEquals(before.getModificationDate(), existing.getModificationDate());
        verify(deckRepository, never()).saveAndFlush(any());
        verify(deckCardRepository, never()).save(any());
        verify(deckCardRepository, never()).deleteByIdDeckId(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"003120065", "0099999"})
    void skipsCompositionMatchWithoutOverwritingExistingVeknId(String existingVeknId) {
        DeckEntity existing = compositionCandidate("legacy").toBuilder().veknId(existingVeknId).build();
        prepareCompositionMatches(List.of(existing));
        ArchonTournament tournament = tournament();

        scheduler.parseDeck(tournament, scheduler.finalists(tournament).getFirst(), deck(), Map.of("winner", "003120065"));

        if (existingVeknId == null || existingVeknId.isBlank()) {
            verify(deckRepository).fillMissingVeknId("legacy", "003120065");
        } else {
            verify(deckRepository, never()).fillMissingVeknId(any(), any());
        }
        assertEquals(existingVeknId, existing.getVeknId());
        verify(deckRepository, never()).saveAndFlush(any());
        verify(deckCardRepository, never()).save(any());
    }

    @Test
    void skipsAmbiguousCompositionMatchesWithoutFillingEitherVeknId() {
        prepareCompositionMatches(List.of(compositionCandidate("first"), compositionCandidate("second")));
        ArchonTournament tournament = tournament();

        scheduler.parseDeck(tournament, scheduler.finalists(tournament).getFirst(), deck(), Map.of("winner", "003120065"));

        verify(deckRepository, never()).fillMissingVeknId(any(), any());
        verify(deckRepository, never()).saveAndFlush(any());
        verify(deckCardRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    void importsIdenticalCompositionOnAnotherDayAsANewTournament(int dayOffset) {
        DeckEntity previous = compositionCandidate("legacy").toBuilder()
                .creationDate(tournament().getStart().plusDays(dayOffset)).build();
        prepareCompositionMatches(List.of(previous));
        ArchonTournament tournament = tournament();

        scheduler.parseDeck(tournament, scheduler.finalists(tournament).getFirst(), deck(), Map.of("winner", "003120065"));

        ArgumentCaptor<DeckEntity> captor = ArgumentCaptor.forClass(DeckEntity.class);
        verify(deckRepository).saveAndFlush(captor.capture());
        assertEquals("tournament-13370", captor.getValue().getId());
        verify(deckRepository, never()).fillMissingVeknId(any(), any());
    }

    private DeckEntity compositionCandidate(String id) {
        return DeckEntity.builder().id(id).type(DeckType.TOURNAMENT).position(1).eventId("legacy-uuid")
                .name("Archived title").author(" ALEX--SMITH ").tournament("Different event label")
                .creationDate(tournament().getStart().toLocalDate().atTime(23, 59)).schedulerOwner(TournamentSchedulerOwner.TWDA)
                .description("Curated description").source("original-source").views(42L).verified(true)
                .finalVp(new BigDecimal("2.0")).finalSeat(5).modificationDate(tournament().getStart()).build();
    }

    private void prepareCompositionMatches(List<DeckEntity> existing) {
        ArchonUserEntity member = new ArchonUserEntity();
        member.setName("Álex Smith");
        when(archonUserRepository.findById("003120065")).thenReturn(Optional.of(member));
        when(deckRepository.findActiveTournamentDecksBetween(any(), any())).thenReturn(existing);
        List<DeckCardEntity> cards = new ArrayList<>();
        for (DeckEntity candidate : existing) {
            for (Map.Entry<Integer, Integer> entry : deck().getCards().entrySet()) {
                DeckCardEntity card = new DeckCardEntity();
                card.setId(new DeckCardEntity.DeckCardId());
                card.getId().setDeckId(candidate.getId());
                card.getId().setCardId(entry.getKey());
                card.setNumber(entry.getValue());
                cards.add(card);
            }
        }
        when(deckCardRepository.findByIdDeckIdIn(any())).thenReturn(cards);
    }

    @Test
    void importsFinalistWhoseDeckHasTheSameNameAsTheWinner() {
        ArchonTournament tournament = tournament();
        DeckEntity winner = DeckEntity.builder().id("winner-deck").type(DeckType.TOURNAMENT)
                .eventId("13370").position(1).name("Winner Deck")
                .tournament("Archon Event").creationDate(tournament.getStart()).build();
        when(deckRepository.findActiveTournamentDecksBetween(tournament.getStart().toLocalDate().atStartOfDay(),
                tournament.getStart().toLocalDate().plusDays(1).atStartOfDay())).thenReturn(List.of(winner));
        TournamentArchonDeckScheduler.Finalist finalist = scheduler.finalists(tournament).get(1);
        ArchonDeck source = deck();
        source.setUserUid(finalist.userUid());

        scheduler.parseDeck(tournament, finalist, source, Map.of(finalist.userUid(), "0012345"));

        ArgumentCaptor<DeckEntity> captor = ArgumentCaptor.forClass(DeckEntity.class);
        verify(deckRepository).saveAndFlush(captor.capture());
        assertEquals(2, captor.getValue().getPosition());
        assertEquals("Winner Deck", captor.getValue().getName());
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
        verify(deckCardRepository, never()).save(any());
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
        assertEquals("3120065", saved.getVeknId());
        assertEquals(123L, saved.getViews());
        assertEquals(actual.getCustomTags(), saved.getCustomTags());
        assertFalse(saved.getVerified());
        var cards = ArgumentCaptor.forClass(com.vtesdecks.jpa.entity.DeckCardEntity.class);
        verify(deckCardRepository, org.mockito.Mockito.times(2)).save(cards.capture());
        org.junit.jupiter.api.Assertions.assertTrue(cards.getAllValues().stream()
                .allMatch(card -> "legacy-id".equals(card.getId().getDeckId())));
    }

    @Test
    void enrichesTwdaUnderDifferentIdWithoutChangingAnyOtherField() {
        DeckEntity actual = DeckEntity.builder().id("twda-legacy-id").type(DeckType.TOURNAMENT)
                .eventId("13370").position(1).schedulerOwner(TournamentSchedulerOwner.TWDA)
                .name("TWDA name").source("twda-source").author("Curated author").views(42L).build();
        DeckEntity expected = actual.toBuilder().finalVp(new BigDecimal("1.5")).finalSeat(1).veknId("003120065").build();
        when(deckRepository.findByTypeAndEventIdAndPositionAndDeletedFalse(DeckType.TOURNAMENT, "13370", 1))
                .thenReturn(List.of(actual));
        ArchonTournament tournament = tournament();
        scheduler.parseDeck(tournament, scheduler.finalists(tournament).getFirst(), deck(), Map.of("winner", "003120065"));
        ArgumentCaptor<DeckEntity> captor = ArgumentCaptor.forClass(DeckEntity.class);
        verify(deckRepository).saveAndFlush(captor.capture());
        assertEquals(expected, captor.getValue());
        org.mockito.Mockito.verifyNoInteractions(deckCardRepository, archonUserRepository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"different-id", "003120065"})
    void preservesTwdaVeknIdWhenEnrichingResults(String incomingId) {
        DeckEntity actual = DeckEntity.builder().id("tournament-13370").type(DeckType.TOURNAMENT)
                .schedulerOwner(TournamentSchedulerOwner.TWDA).veknId("003120065").author("Curated author").build();
        when(deckRepository.findById(actual.getId())).thenReturn(Optional.of(actual));
        ArchonTournament tournament = tournament();

        scheduler.parseDeck(tournament, scheduler.finalists(tournament).getFirst(), deck(),
                incomingId == null ? Map.of() : Map.of("winner", incomingId));

        verify(deckRepository).saveAndFlush(actual);
        assertEquals("003120065", actual.getVeknId());
        assertEquals("Curated author", actual.getAuthor());
        org.mockito.Mockito.verifyNoInteractions(deckCardRepository, archonUserRepository);
    }

    @Test
    void fillsTwdaVeknIdEvenWhenFinalResultIsAlreadyCurrent() {
        DeckEntity actual = DeckEntity.builder().id("tournament-13370").type(DeckType.TOURNAMENT)
                .schedulerOwner(TournamentSchedulerOwner.TWDA).finalVp(new BigDecimal("1.5")).finalSeat(1).build();
        when(deckRepository.findById(actual.getId())).thenReturn(Optional.of(actual));
        ArchonTournament tournament = tournament();

        scheduler.parseDeck(tournament, scheduler.finalists(tournament).getFirst(), deck(), Map.of("winner", "003120065"));

        verify(deckRepository).saveAndFlush(actual);
        assertEquals("003120065", actual.getVeknId());
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
        scheduler.parseDeck(tournament, scheduler.finalists(tournament).getFirst(), deck(), Map.of("winner", "003120065"));
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

    private void importExport(ArchonTournament tournament, List<ArchonDeck> decks, Map<String, String> veknIds) throws Exception {
        ObjectMapper mapper = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .findAndRegisterModules();
        StringBuilder ndjson = new StringBuilder(mapper.writeValueAsString(Map.of("type", "tournament", "data", tournament)))
                .append('\n');
        for (ArchonDeck deck : decks) {
            deck.setTournamentUid(tournament.getUid());
            ndjson.append(mapper.writeValueAsString(Map.of("type", "deck", "data", deck))).append('\n');
        }
        for (Map.Entry<String, String> user : veknIds.entrySet()) {
            ndjson.append(mapper.writeValueAsString(Map.of("type", "user", "data",
                    Map.of("uid", user.getKey(), "vekn_id", user.getValue())))).append('\n');
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(ndjson.toString().getBytes(StandardCharsets.UTF_8));
        }
        ReflectionTestUtils.setField(scheduler, "clientId", "test-client");
        ReflectionTestUtils.setField(scheduler, "clientSecret", "test-secret");
        ArchonAccessToken token = new ArchonAccessToken();
        token.setAccessToken("test-token");
        when(archonClient.token(any())).thenReturn(token);
        when(archonClient.export("Bearer test-token")).thenReturn(Response.builder().status(200).reason("OK")
                .headers(Map.of()).request(Request.create(Request.HttpMethod.GET, "https://archon.test/export",
                        Map.of(), null, StandardCharsets.UTF_8, null)).body(bytes.toByteArray()).build());

        scheduler.scrappingDecks();
    }

    @Test
    void stillUpdatesAnExistingArchonDeckWithoutVeknId(CapturedOutput output) {
        DeckEntity actual = DeckEntity.builder().id("tournament-13370").type(DeckType.TOURNAMENT)
                .eventId("13370").position(1).schedulerOwner(TournamentSchedulerOwner.ARCHON).verified(false).views(0L).build();
        when(deckRepository.findById(actual.getId())).thenReturn(Optional.of(actual));
        ArchonTournament tournament = tournament();

        scheduler.parseDeck(tournament, scheduler.finalists(tournament).getFirst(), deck(), Map.of());

        ArgumentCaptor<DeckEntity> captor = ArgumentCaptor.forClass(DeckEntity.class);
        verify(deckRepository).saveAndFlush(captor.capture());
        org.junit.jupiter.api.Assertions.assertNull(captor.getValue().getVeknId());
        assertEquals("Winner Deck", captor.getValue().getName());
        assertFalse(output.getAll().contains("eventId and veknId are required"));
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
