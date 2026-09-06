package com.vtesdecks.scheduler;

import com.vtesdecks.jpa.entity.CollectionEntity;
import com.vtesdecks.jpa.repositories.CollectionBinderRepository;
import com.vtesdecks.jpa.repositories.CollectionCardHistoryRepository;
import com.vtesdecks.jpa.repositories.CollectionCardRepository;
import com.vtesdecks.jpa.repositories.CollectionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CleanUpSchedulerTest {
    @Mock
    private CollectionRepository collectionRepository;
    @Mock
    private CollectionCardRepository collectionCardRepository;
    @Mock
    private CollectionCardHistoryRepository collectionCardHistoryRepository;
    @Mock
    private CollectionBinderRepository collectionBinderRepository;
    @InjectMocks
    private CleanUpScheduler scheduler;

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
}
