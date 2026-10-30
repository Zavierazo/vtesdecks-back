package com.vtesdecks.scheduler.tournament;

import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.jpa.entity.DeckEntity;
import com.vtesdecks.jpa.entity.DeckCardEntity;
import com.vtesdecks.jpa.entity.TournamentSchedulerOwner;
import com.vtesdecks.jpa.repositories.DeckRepository;
import com.vtesdecks.jpa.repositories.DeckCardRepository;
import com.vtesdecks.scheduler.tournament.helpers.TournamentImportPolicy;
import com.vtesdecks.scheduler.tournament.helpers.TournamentImportPolicy.Action;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TournamentImportPolicyTest {
    private DeckRepository repository;
    private DeckCardRepository cardRepository;
    private PlatformTransactionManager transactionManager;
    private TournamentImportPolicy policy;
    private BiConsumer<DeckEntity, Action> importer;

    @ParameterizedTest
    @CsvSource({"123,003120065,true", ",003120065,false", "'',003120065,false", "'  ',003120065,false",
            "123,,false", "123,'',false", "123,'  ',false", ",,false"})
    void requiresBothNonblankIdsOnlyForNewDecks(String eventId, String veknId, boolean allowed) {
        DeckEntity deck = DeckEntity.builder().id("candidate").type(DeckType.TOURNAMENT)
                .eventId(eventId).veknId(veknId).schedulerOwner(TournamentSchedulerOwner.TWDA).build();
        assertEquals(allowed, policy.canPersist(null, deck));
        assertTrue(policy.canPersist(deck, deck));
        verifyNoInteractions(repository, cardRepository, transactionManager);
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(DeckRepository.class);
        cardRepository = mock(DeckCardRepository.class);
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        policy = new TournamentImportPolicy(repository, cardRepository, new TransactionTemplate(transactionManager));
        importer = mock(BiConsumer.class);
    }

    @ParameterizedTest
    @CsvSource({
            "TWDA,TWDA,IMPORT", "TWDA,ARCHON,IMPORT", "TWDA,ETERNAL_VIGILANCE,IMPORT",
            "ARCHON,TWDA,ENRICH_METADATA", "ARCHON,ARCHON,IMPORT", "ARCHON,ETERNAL_VIGILANCE,IMPORT",
            "ETERNAL_VIGILANCE,TWDA,SKIP", "ETERNAL_VIGILANCE,ARCHON,SKIP", "ETERNAL_VIGILANCE,ETERNAL_VIGILANCE,IMPORT"
    })
    void enforcesEveryPriorityPair(TournamentSchedulerOwner incoming, TournamentSchedulerOwner owner, String action) {
        DeckEntity actual = existing(owner);
        when(repository.findById("incoming")).thenReturn(Optional.of(actual));
        policy.execute("incoming", "123", 1, incoming, importer);
        if ("SKIP".equals(action)) {
            verifyNoInteractions(importer);
        } else {
            verify(importer).accept(actual, Action.valueOf(action));
        }
        verify(transactionManager).commit(any());
    }

    @ParameterizedTest
    @EnumSource(TournamentSchedulerOwner.class)
    void restrictsVerifiedDecksToReportingOrMetadataEnrichment(TournamentSchedulerOwner owner) {
        DeckEntity actual = existing(owner);
        actual.setVerified(true);
        when(repository.findById("incoming")).thenReturn(Optional.of(actual));
        for (TournamentSchedulerOwner incoming : TournamentSchedulerOwner.values()) {
            clearInvocations(importer);
            policy.execute("incoming", "123", 1, incoming, importer);
            if (incoming == TournamentSchedulerOwner.TWDA) {
                verify(importer).accept(actual, Action.REPORT_VERIFIED);
            } else if (incoming == TournamentSchedulerOwner.ARCHON) {
                verify(importer).accept(actual, Action.ENRICH_METADATA);
            } else {
                verifyNoInteractions(importer);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(TournamentSchedulerOwner.class)
    void protectsUnknownOwnersEvenWithRecognizableSource(TournamentSchedulerOwner incoming) {
        DeckEntity actual = existing(null);
        actual.setSource("https://archon.vekn.net/tournaments/example");
        when(repository.findById("incoming")).thenReturn(Optional.of(actual));
        policy.execute("incoming", "123", 1, incoming, importer);
        verifyNoInteractions(importer);
    }

    @Test
    void resolvesDifferentIdByEventAndPositionBeforeCallingImporter() {
        DeckEntity actual = existing(TournamentSchedulerOwner.ETERNAL_VIGILANCE);
        actual.setId("existing-id");
        when(repository.findByTypeAndEventIdAndPositionAndDeletedFalse(DeckType.TOURNAMENT, "123", 1))
                .thenReturn(List.of(actual));
        policy.execute("incoming", "123", 1, TournamentSchedulerOwner.ARCHON, importer);
        var order = inOrder(transactionManager, repository, importer);
        order.verify(transactionManager).getTransaction(any());
        order.verify(repository).findById("incoming");
        order.verify(repository).findByTypeAndEventIdAndPositionAndDeletedFalse(DeckType.TOURNAMENT, "123", 1);
        order.verify(importer).accept(actual, Action.IMPORT);
        order.verify(transactionManager).commit(any());
    }

    @Test
    void rejectsConflictingRowsInsteadOfMergingThem() {
        DeckEntity first = existing(TournamentSchedulerOwner.ARCHON);
        DeckEntity second = first.toBuilder().id("other").build();
        when(repository.findById("incoming")).thenReturn(Optional.of(first));
        when(repository.findByTypeAndEventIdAndPositionAndDeletedFalse(DeckType.TOURNAMENT, "123", 1))
                .thenReturn(List.of(second));
        policy.execute("incoming", "123", 1, TournamentSchedulerOwner.ARCHON, importer);
        verifyNoInteractions(importer);
    }

    @Test
    void rejectsMultipleEventMatchesEvenWithoutAnIdMatch() {
        DeckEntity first = existing(TournamentSchedulerOwner.ARCHON);
        when(repository.findByTypeAndEventIdAndPositionAndDeletedFalse(DeckType.TOURNAMENT, "123", 1))
                .thenReturn(List.of(first, first.toBuilder().id("other").build()));
        policy.execute("incoming", "123", 1, TournamentSchedulerOwner.ARCHON, importer);
        verifyNoInteractions(importer);
    }

    @Test
    void sameRowReturnedByBothQueriesIsNotAConflict() {
        DeckEntity actual = existing(TournamentSchedulerOwner.ARCHON);
        when(repository.findById("incoming")).thenReturn(Optional.of(actual));
        when(repository.findByTypeAndEventIdAndPositionAndDeletedFalse(DeckType.TOURNAMENT, "123", 1))
                .thenReturn(List.of(actual));
        policy.execute("incoming", "123", 1, TournamentSchedulerOwner.TWDA, importer);
        verify(importer).accept(actual, Action.IMPORT);
    }

    @Test
    void rejectsIncompatibleOrDeletedIdMatches() {
        DeckEntity actual = existing(TournamentSchedulerOwner.ARCHON);
        for (DeckEntity conflict : List.of(actual.toBuilder().deleted(true).build(),
                actual.toBuilder().type(DeckType.COMMUNITY).build(),
                actual.toBuilder().eventId("other-event").build(), actual.toBuilder().position(2).build())) {
            when(repository.findById("incoming")).thenReturn(Optional.of(conflict));
            policy.execute("incoming", "123", 1, TournamentSchedulerOwner.ARCHON, importer);
        }
        verifyNoInteractions(importer);
    }

    @Test
    void importsNewDeckWithoutRequiringAnEventId() {
        policy.execute("incoming", null, 1, TournamentSchedulerOwner.TWDA, importer);
        verify(importer).accept(null, Action.IMPORT);
        verify(repository, never()).findByTypeAndEventIdAndPositionAndDeletedFalse(any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 8, 15})
    void detectsNormalizedTournamentAndPlayerOnTheSameDayRegardlessOfDeckName(int hourOffset) {
        DeckEntity deck = duplicateCandidate();
        DeckEntity other = deck.toBuilder().id("legacy").name("Incorrect archived title").veknId("1001234")
                .tournament(" MÉTRO  Championship ").creationDate(deck.getCreationDate().plusHours(hourOffset)).build();
        when(repository.findActiveTournamentDecksBetween(LocalDateTime.of(2026, 10, 3, 0, 0),
                LocalDateTime.of(2026, 10, 4, 0, 0))).thenReturn(List.of(other));

        assertTrue(policy.hasImportConflict(null, deck, List.of()));
    }

    @ParameterizedTest
    @CsvSource({"1,1,true", "1,2,false", "2,1,false", "2,2,true", "1,,true", "2,,false", ",1,true", ",2,false", ",,true"})
    void distinguishesFinalistsAndTreatsLegacyPositionsAsWinners(Integer incoming, Integer previous, boolean duplicate) {
        DeckEntity deck = duplicateCandidate().toBuilder().position(incoming).build();
        DeckEntity other = deck.toBuilder().id("legacy").position(previous).build();
        when(repository.findActiveTournamentDecksBetween(any(), any())).thenReturn(List.of(other));

        assertEquals(duplicate, policy.hasImportConflict(null, deck, List.of()));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Previous deck title", "Incorrect archived title"})
    void ignoresDeckNamesWhenTournamentDateAndPlayerMatch(String previousName) {
        DeckEntity deck = duplicateCandidate();
        DeckEntity other = deck.toBuilder().id("legacy").name(previousName).build();
        when(repository.findActiveTournamentDecksBetween(any(), any())).thenReturn(List.of(other));

        assertTrue(policy.hasImportConflict(null, deck, List.of()));
    }

    @Test
    void doesNotDeduplicateMatchingDeckNamesWhenPlayersDiffer() {
        DeckEntity deck = duplicateCandidate();
        DeckEntity other = deck.toBuilder().id("other").veknId("9999999").build();
        when(repository.findActiveTournamentDecksBetween(any(), any())).thenReturn(List.of(other));

        assertFalse(policy.hasImportConflict(null, deck, List.of()));
    }

    @Test
    void doesNotFallBackToMetadataWhenBothEventIdsAreKnownAndDifferent() {
        DeckEntity deck = duplicateCandidate().toBuilder().eventId("123").build();
        DeckEntity other = deck.toBuilder().id("other").eventId("456").build();
        when(repository.findActiveTournamentDecksBetween(any(), any())).thenReturn(List.of(other));

        assertFalse(policy.hasImportConflict(null, deck, List.of()));
    }

    @Test
    void ignoresTheSameRowAndOtherTournaments() {
        DeckEntity deck = duplicateCandidate();
        DeckEntity other = deck.toBuilder().id("other").tournament("Another championship").build();
        when(repository.findActiveTournamentDecksBetween(any(), any())).thenReturn(List.of(deck, other));

        assertFalse(policy.hasImportConflict(null, deck, List.of()));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void requiresVeknIdForLegacyMetadataMatching(String veknId) {
        DeckEntity deck = duplicateCandidate().toBuilder().veknId(veknId).build();

        assertFalse(policy.hasImportConflict(null, deck, List.of()));

        verify(repository, never()).findActiveTournamentDecksBetween(any(), any());
    }

    @Test
    void skipsLegacyMatchingWhenDateOrTournamentIsUnknown() {
        DeckEntity deck = duplicateCandidate();

        assertFalse(policy.hasImportConflict(null, deck.toBuilder().creationDate(null).build(), List.of()));
        assertFalse(policy.hasImportConflict(null, deck.toBuilder().tournament(null).build(), List.of()));
        assertFalse(policy.hasImportConflict(null, deck.toBuilder().tournament(" -- ").build(), List.of()));

        verify(repository, never()).findActiveTournamentDecksBetween(any(), any());
    }

    @Test
    void letsResolvedDecksBeUpdatedWithoutApplyingTheNewDeckDuplicateGuard() {
        DeckEntity deck = duplicateCandidate();

        assertFalse(policy.hasImportConflict(deck, deck.toBuilder().name("Updated title").build(), List.of()));

        verify(repository, never()).findActiveTournamentDecksBetween(any(), any());
    }

    @Test
    void checksEventAndPositionConflictsBeforeMatchingMetadata() {
        DeckEntity deck = duplicateCandidate().toBuilder().eventId("123")
                .creationDate(null).tournament(null).veknId(null).build();
        when(repository.findByTypeAndEventIdAndPositionAndDeletedFalse(DeckType.TOURNAMENT, "123", 1))
                .thenReturn(List.of(deck.toBuilder().id("existing").build()));

        assertTrue(policy.hasImportConflict(null, deck, List.of()));

        verify(repository, never()).findActiveTournamentDecksBetween(any(), any());
    }

    @ParameterizedTest
    @CsvSource({"123,", ",123", ","})
    void fallsBackToTournamentDateAndPlayerWhenEitherEventIdIsMissing(String eventId, String previousEventId) {
        DeckEntity deck = duplicateCandidate().toBuilder().eventId(eventId).build();
        DeckEntity other = deck.toBuilder().id("legacy").eventId(previousEventId).name("Different title").build();
        when(repository.findActiveTournamentDecksBetween(any(), any())).thenReturn(List.of(other));

        assertTrue(policy.hasImportConflict(null, deck, List.of()));
    }

    @Test
    void matchesKnownEventIdsDespiteDifferentMetadataForLegacyWinnerPositions() {
        DeckEntity deck = duplicateCandidate().toBuilder().eventId("123").veknId(null).tournament(null).build();
        DeckEntity other = deck.toBuilder().id("legacy").position(null).name("Different title").build();
        when(repository.findActiveTournamentDecksBetween(any(), any())).thenReturn(List.of(other));

        assertTrue(policy.hasImportConflict(null, deck, List.of()));
    }

    @Test
    void returnsAllLegacyDuplicatesSoCallersCanAvoidAmbiguousEnrichment() {
        DeckEntity deck = duplicateCandidate();
        DeckEntity first = deck.toBuilder().id("first").build();
        DeckEntity second = deck.toBuilder().id("second").build();
        when(repository.findActiveTournamentDecksBetween(any(), any())).thenReturn(List.of(first, second));

        assertEquals(List.of(first, second), policy.findDuplicates(null, deck, List.of()));
    }

    @Test
    void returnsAllEventIdDuplicatesExceptTheCandidateItself() {
        DeckEntity deck = duplicateCandidate().toBuilder().eventId("123").build();
        DeckEntity first = deck.toBuilder().id("first").build();
        DeckEntity second = deck.toBuilder().id("second").build();
        when(repository.findByTypeAndEventIdAndPositionAndDeletedFalse(DeckType.TOURNAMENT, "123", 1))
                .thenReturn(List.of(deck, first, second));

        assertEquals(List.of(first, second), policy.findDuplicates(null, deck, List.of()));
        verify(repository, never()).findActiveTournamentDecksBetween(any(), any());
    }

    @Test
    void rollsBackWhenPersistenceFails() {
        doThrow(new IllegalStateException("Card persistence failed")).when(importer).accept(null, Action.IMPORT);
        assertThrows(IllegalStateException.class,
                () -> policy.execute("incoming", "123", 1, TournamentSchedulerOwner.ARCHON, importer));
        verify(transactionManager).rollback(any());
        verify(transactionManager, never()).commit(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 12, 23})
    void matchesExactCardsAndPlayerOnTheSameDayDespiteDifferentEventIdsAndNames(int hour) {
        DeckEntity deck = duplicateCandidate().toBuilder().eventId("123").build();
        DeckEntity other = deck.toBuilder().id("legacy").eventId("legacy-uuid").tournament("Another label")
                .veknId("1001234").creationDate(deck.getCreationDate().toLocalDate().atTime(hour, 59)).build();
        when(repository.findActiveTournamentDecksBetween(any(), any())).thenReturn(List.of(other));
        when(cardRepository.findByIdDeckIdIn(List.of("legacy")))
                .thenReturn(List.of(card("legacy", 100001, 60), card("legacy", 200001, 12)));

        assertEquals(List.of(other), policy.findDuplicates(null, deck, cards()));
        verify(repository).findActiveTournamentDecksBetween(LocalDateTime.of(2026, 10, 3, 0, 0), LocalDateTime.of(2026, 10, 4, 0, 0));
    }

    @ParameterizedTest
    @ValueSource(ints = {-2, -1, 1, 2})
    void acceptsIdenticalListsAndMetadataOnDifferentCalendarDays(int offset) {
        DeckEntity deck = duplicateCandidate();
        DeckEntity other = deck.toBuilder().id("legacy").creationDate(deck.getCreationDate().plusDays(offset)).build();
        when(repository.findActiveTournamentDecksBetween(any(), any())).thenReturn(List.of(other));

        assertFalse(policy.hasImportConflict(null, deck, cards()));
        verifyNoInteractions(cardRepository);
    }

    @ParameterizedTest
    @CsvSource({"200002,12,100001,60", "200001,11,100001,60", "200001,12,100002,60", "200001,12,100001,59", "200001,12,100001,61"})
    void requiresEveryCryptAndLibraryCardAndQuantityToMatch(int crypt, int cryptCount, int library, int libraryCount) {
        DeckEntity deck = duplicateCandidate().toBuilder().eventId("123").build();
        DeckEntity other = deck.toBuilder().id("legacy").eventId("different").tournament("Different name").build();
        when(repository.findActiveTournamentDecksBetween(any(), any())).thenReturn(List.of(other));
        when(cardRepository.findByIdDeckIdIn(List.of("legacy")))
                .thenReturn(List.of(card("legacy", crypt, cryptCount), card("legacy", library, libraryCount)));

        assertFalse(policy.hasImportConflict(null, deck, cards()));
    }

    @Test
    void requiresCompleteNonemptyCompositionsAndMatchingPlayersAndPositions() {
        DeckEntity deck = duplicateCandidate().toBuilder().eventId("123").build();
        DeckEntity other = deck.toBuilder().id("legacy").eventId("different").tournament("Different name").build();
        when(repository.findActiveTournamentDecksBetween(any(), any())).thenReturn(List.of(other));
        for (List<DeckCardEntity> stored : List.of(List.<DeckCardEntity>of(), List.of(card("legacy", 200001, 12)),
                List.of(card("legacy", 200001, 12), card("legacy", 100001, 60), card("legacy", 100002, 1)),
                List.of(card("legacy", 200001, 12), card("legacy", 100001, 0)))) {
            when(cardRepository.findByIdDeckIdIn(List.of("legacy"))).thenReturn(stored);
            assertFalse(policy.hasImportConflict(null, deck, cards()));
        }
        assertFalse(policy.hasImportConflict(null, deck, List.of()));
        when(repository.findActiveTournamentDecksBetween(any(), any()))
                .thenReturn(List.of(other.toBuilder().veknId("9999999").build(), other.toBuilder().position(2).build()));
        clearInvocations(cardRepository);
        assertFalse(policy.hasImportConflict(null, deck, cards()));
        verifyNoInteractions(cardRepository);
    }

    @Test
    void returnsAllCompositionMatchesWithoutRequiringATournamentNameOrEventId() {
        DeckEntity deck = duplicateCandidate().toBuilder().tournament(null).build();
        DeckEntity first = deck.toBuilder().id("first").build();
        DeckEntity second = deck.toBuilder().id("second").build();
        when(repository.findActiveTournamentDecksBetween(any(), any())).thenReturn(List.of(first, second));
        when(cardRepository.findByIdDeckIdIn(List.of("first", "second"))).thenReturn(List.of(
                card("first", 200001, 12), card("first", 100001, 60), card("second", 200001, 12), card("second", 100001, 60)));

        assertEquals(List.of(first, second), policy.findDuplicates(null, deck, cards()));
        verify(cardRepository).findByIdDeckIdIn(List.of("first", "second"));
    }

    @Test
    void blocksUnidentifiedCompositionWithoutClaimingThePlayerIdentity() {
        DeckEntity deck = duplicateCandidate().toBuilder().eventId("new-event").build();
        DeckEntity existing = deck.toBuilder().id("legacy").eventId("legacy-event").veknId(null).build();
        when(repository.findActiveTournamentDecksBetween(any(), any())).thenReturn(List.of(existing));
        when(cardRepository.findByIdDeckIdIn(List.of("legacy")))
                .thenReturn(List.of(card("legacy", 200001, 12), card("legacy", 100001, 60)));

        assertEquals(List.of(existing), policy.findDuplicates(null, deck, cards()));
        assertFalse(policy.canEnrichDuplicate(existing, deck));
        assertTrue(policy.canEnrichDuplicate(existing.toBuilder().eventId("new-event").build(), deck));
        assertFalse(policy.canEnrichDuplicate(existing.toBuilder().eventId("new-event").position(2).build(), deck));
    }

    private List<DeckCardEntity> cards() {
        return List.of(card("incoming", 200001, 12), card("incoming", 100001, 60));
    }

    private DeckCardEntity card(String deckId, int cardId, int number) {
        DeckCardEntity card = new DeckCardEntity();
        card.setId(new DeckCardEntity.DeckCardId());
        card.getId().setDeckId(deckId);
        card.getId().setCardId(cardId);
        card.setNumber(number);
        return card;
    }

    private DeckEntity existing(TournamentSchedulerOwner owner) {
        return DeckEntity.builder().id("incoming").type(DeckType.TOURNAMENT)
                .eventId("123").position(1).schedulerOwner(owner).build();
    }

    private DeckEntity duplicateCandidate() {
        return existing(TournamentSchedulerOwner.ARCHON).toBuilder().eventId(null).name("Café Deck").veknId("1001234")
                .tournament("Metro Championship").creationDate(LocalDateTime.of(2026, 10, 3, 8, 0)).build();
    }
}
