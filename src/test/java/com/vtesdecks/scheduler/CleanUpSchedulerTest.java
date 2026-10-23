package com.vtesdecks.scheduler;

import com.vtesdecks.jpa.entity.CollectionEntity;
import com.vtesdecks.jpa.entity.DeckViewEntity;
import com.vtesdecks.jpa.repositories.DeckRepository;
import com.vtesdecks.jpa.repositories.DeckViewRepository;
import com.vtesdecks.jpa.repositories.CollectionBinderRepository;
import com.vtesdecks.jpa.repositories.CollectionCardHistoryRepository;
import com.vtesdecks.jpa.repositories.CollectionCardRepository;
import com.vtesdecks.jpa.repositories.CollectionRepository;
import com.vtesdecks.service.DatabaseCleanupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.time.LocalDateTime;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CleanUpSchedulerTest {
    @Mock
    private DeckRepository deckRepository;
    @Mock
    private DeckViewRepository deckViewRepository;
    @Mock
    private CollectionRepository collectionRepository;
    @Mock
    private CollectionCardRepository collectionCardRepository;
    @Mock
    private CollectionCardHistoryRepository collectionCardHistoryRepository;
    @Mock
    private CollectionBinderRepository collectionBinderRepository;
    @Mock
    private DatabaseCleanupService databaseCleanupService;
    @InjectMocks
    private CleanUpScheduler scheduler;

    @Test
    void consolidatesViewsWithoutSavingDeckEntities() {
        DeckViewEntity first = new DeckViewEntity();
        first.setId(new DeckViewEntity.DeckViewId("first", "preconstructed-noites_brasileiras"));
        DeckViewEntity second = new DeckViewEntity();
        second.setId(new DeckViewEntity.DeckViewId("second", "preconstructed-noites_brasileiras"));
        List<DeckViewEntity> views = List.of(first, second);
        when(deckViewRepository.findOld()).thenReturn(views);

        scheduler.deckViewCleanScheduler();

        InOrder order = inOrder(deckRepository, deckViewRepository);
        order.verify(deckViewRepository).findOld();
        order.verify(deckRepository).incrementViews("preconstructed-noites_brasileiras", 2);
        order.verify(deckViewRepository).deleteAll(views);
        order.verify(deckViewRepository).flush();
        verifyNoMoreInteractions(deckRepository, deckViewRepository);
    }

    @Test
    void doesNotUpdateDecksWhenNoViewsHaveExpired() {
        when(deckViewRepository.findOld()).thenReturn(List.of());

        scheduler.deckViewCleanScheduler();

        verifyNoInteractions(deckRepository);
        verify(deckViewRepository).findOld();
        verifyNoMoreInteractions(deckViewRepository);
    }

    @Test
    void removesRelatedDataInTriggerAndForeignKeySafeOrderForEachExpiredCollection() {
        CollectionEntity first = new CollectionEntity();
        first.setId(10);
        first.setDeleted(true);
        CollectionEntity second = new CollectionEntity();
        second.setId(20);
        second.setDeleted(true);
        when(collectionRepository.selectOldDeleted()).thenReturn(List.of(first, second));

        scheduler.collectionCleanScheduler();

        InOrder order = inOrder(collectionRepository, collectionCardRepository,
                collectionCardHistoryRepository, collectionBinderRepository);
        order.verify(collectionRepository).selectOldDeleted();
        for (Integer id : List.of(10, 20)) {
            order.verify(collectionCardRepository).deleteByCollectionId(id);
            order.verify(collectionCardRepository).flush();
            order.verify(collectionCardHistoryRepository).deleteByCollectionId(id);
            order.verify(collectionCardHistoryRepository).flush();
            order.verify(collectionBinderRepository).deleteByCollectionId(id);
            order.verify(collectionBinderRepository).flush();
            order.verify(collectionRepository).deleteById(id);
            order.verify(collectionRepository).flush();
        }
        verifyNoMoreInteractions(collectionRepository, collectionCardRepository,
                collectionCardHistoryRepository, collectionBinderRepository);
    }

    @Test
    void doesNotDeleteRelatedDataWhenNoCollectionsHaveExpired() {
        when(collectionRepository.selectOldDeleted()).thenReturn(List.of());

        scheduler.collectionCleanScheduler();

        verifyNoInteractions(collectionCardRepository, collectionCardHistoryRepository,
                collectionBinderRepository);
        verify(collectionRepository).selectOldDeleted();
        verifyNoMoreInteractions(collectionRepository);
    }

    @Test
    void deletesNotificationsInBoundedBatches() {
        when(databaseCleanupService.deleteOldReadNotificationsBatch(any(LocalDateTime.class)))
                .thenReturn(DatabaseCleanupService.BATCH_SIZE, 3);

        scheduler.notificationsCleanScheduler();

        verify(databaseCleanupService, times(2))
                .deleteOldReadNotificationsBatch(any(LocalDateTime.class));
    }

    @Test
    void reportsBlockedCommentsAfterDeletingAllAvailableLeaves() {
        when(databaseCleanupService.deleteEligibleCommentsBatch(any(LocalDateTime.class))).thenReturn(0);
        when(databaseCleanupService.countBlockedEligibleComments(any(LocalDateTime.class))).thenReturn(2L);

        scheduler.commentsCleanScheduler();

        verify(databaseCleanupService).deleteEligibleCommentsBatch(any(LocalDateTime.class));
        verify(databaseCleanupService).countBlockedEligibleComments(any(LocalDateTime.class));
    }
}
