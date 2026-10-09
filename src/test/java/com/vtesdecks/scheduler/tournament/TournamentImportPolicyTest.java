package com.vtesdecks.scheduler.tournament;

import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.jpa.entity.DeckEntity;
import com.vtesdecks.jpa.entity.TournamentSchedulerOwner;
import com.vtesdecks.jpa.repositories.DeckRepository;
import com.vtesdecks.scheduler.tournament.helpers.TournamentImportPolicy;
import com.vtesdecks.scheduler.tournament.helpers.TournamentImportPolicy.Action;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TournamentImportPolicyTest {
    private DeckRepository repository;
    private PlatformTransactionManager transactionManager;
    private TournamentImportPolicy policy;
    private BiConsumer<DeckEntity, Action> importer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(DeckRepository.class);
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        policy = new TournamentImportPolicy(repository, new TransactionTemplate(transactionManager));
        importer = mock(BiConsumer.class);
    }

    @ParameterizedTest
    @CsvSource({
            "TWDA,TWDA,IMPORT", "TWDA,ARCHON,IMPORT", "TWDA,ETERNAL_VIGILANCE,IMPORT",
            "ARCHON,TWDA,ENRICH_FINAL_RESULT", "ARCHON,ARCHON,IMPORT", "ARCHON,ETERNAL_VIGILANCE,IMPORT",
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
    void protectsVerifiedDecksOfEveryOwner(TournamentSchedulerOwner owner) {
        DeckEntity actual = existing(owner);
        actual.setVerified(true);
        when(repository.findById("incoming")).thenReturn(Optional.of(actual));
        for (TournamentSchedulerOwner incoming : TournamentSchedulerOwner.values()) {
            clearInvocations(importer);
            policy.execute("incoming", "123", 1, incoming, importer);
            if (incoming == TournamentSchedulerOwner.TWDA) {
                verify(importer).accept(actual, Action.REPORT_VERIFIED);
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
        policy.execute("incoming", "123", 1, TournamentSchedulerOwner.TWDA, importer);
        verifyNoInteractions(importer);
    }

    @Test
    void rejectsMultipleEventMatchesEvenWithoutAnIdMatch() {
        DeckEntity first = existing(TournamentSchedulerOwner.ARCHON);
        when(repository.findByTypeAndEventIdAndPositionAndDeletedFalse(DeckType.TOURNAMENT, "123", 1))
                .thenReturn(List.of(first, first.toBuilder().id("other").build()));
        policy.execute("incoming", "123", 1, TournamentSchedulerOwner.TWDA, importer);
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
            policy.execute("incoming", "123", 1, TournamentSchedulerOwner.TWDA, importer);
        }
        verifyNoInteractions(importer);
    }

    @Test
    void importsNewDeckWithoutRequiringAnEventId() {
        policy.execute("incoming", null, 1, TournamentSchedulerOwner.TWDA, importer);
        verify(importer).accept(null, Action.IMPORT);
        verify(repository, never()).findByTypeAndEventIdAndPositionAndDeletedFalse(any(), any(), any());
    }

    @Test
    void rollsBackWhenPersistenceFails() {
        doThrow(new IllegalStateException("Card persistence failed")).when(importer).accept(null, Action.IMPORT);
        assertThrows(IllegalStateException.class,
                () -> policy.execute("incoming", "123", 1, TournamentSchedulerOwner.ARCHON, importer));
        verify(transactionManager).rollback(any());
        verify(transactionManager, never()).commit(any());
    }

    @Test
    void prefetchRejectionAvoidsDownloadingProtectedDecksButDoesNotAuthorizeWrites() {
        for (TournamentSchedulerOwner owner : TournamentSchedulerOwner.values()) {
            when(repository.findById("incoming")).thenReturn(Optional.of(existing(owner)));
            assertEquals(owner != TournamentSchedulerOwner.ETERNAL_VIGILANCE,
                    policy.shouldSkipFetch("incoming", TournamentSchedulerOwner.ETERNAL_VIGILANCE));
        }
        // Import decisions are based on the current owner when execute is called.
        when(repository.findById("incoming"))
                .thenReturn(Optional.of(existing(TournamentSchedulerOwner.TWDA)));
        policy.execute("incoming", "123", 1, TournamentSchedulerOwner.ETERNAL_VIGILANCE, importer);
        verifyNoInteractions(importer);
    }

    private DeckEntity existing(TournamentSchedulerOwner owner) {
        return DeckEntity.builder().id("incoming").type(DeckType.TOURNAMENT)
                .eventId("123").position(1).schedulerOwner(owner).build();
    }
}
