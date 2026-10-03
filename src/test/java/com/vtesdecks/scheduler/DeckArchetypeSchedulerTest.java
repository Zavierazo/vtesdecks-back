package com.vtesdecks.scheduler;

import com.vtesdecks.cache.DeckCardIndex;
import com.vtesdecks.cache.indexable.DeckSummary;
import com.vtesdecks.jpa.entity.DeckArchetypeEntity;
import com.vtesdecks.jpa.entity.DeckEntity;
import com.vtesdecks.jpa.repositories.DeckArchetypeRepository;
import com.vtesdecks.jpa.repositories.DeckRepository;
import com.vtesdecks.messaging.MessageProducer;
import com.vtesdecks.model.ArchetypeCardRequirement;
import com.vtesdecks.service.DeckService;
import com.vtesdecks.util.CosineSimilarityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeckArchetypeSchedulerTest {
    @Mock DeckCardIndex deckCardIndex;
    @Mock com.vtesdecks.cache.CryptCache cryptCache;
    @Mock com.vtesdecks.cache.LibraryCache libraryCache;
    @Mock DeckService deckService;
    @Mock DeckRepository deckRepository;
    @Mock DeckArchetypeRepository deckArchetypeRepository;
    @Mock MessageProducer messageProducer;
    @InjectMocks DeckArchetypeScheduler scheduler;

    private DeckEntity setup(Map<Integer, Integer> cards, List<ArchetypeCardRequirement> rules, Integer assigned) {
        DeckEntity entity = DeckEntity.builder().id("candidate").deckArchetypeId(assigned).build();
        when(deckRepository.findAll()).thenReturn(List.of(entity));
        when(deckArchetypeRepository.findAll()).thenReturn(List.of(archetype(1, "reference", rules)));
        summary("candidate", cards);
        summary("reference", Map.of(200001, 4, 200002, 1, 100001, 10));
        return entity;
    }

    private DeckArchetypeEntity archetype(int id, String reference, List<ArchetypeCardRequirement> rules) {
        return DeckArchetypeEntity.builder().id(id).deckId(reference).cardRequirements(rules).build();
    }

    private void summary(String id, Map<Integer, Integer> cards) {
        DeckSummary summary = mock(DeckSummary.class);
        when(summary.getId()).thenReturn(id);
        lenient().when(summary.getL2Norm()).thenReturn(CosineSimilarityUtils.computeL2Norm(cards));
        when(deckService.getSummary(id)).thenReturn(summary);
        when(deckCardIndex.getCardCounts(id)).thenReturn(cards);
    }

    private List<ArchetypeCardRequirement> rules() {
        return List.of(new ArchetypeCardRequirement(200001, 4), new ArchetypeCardRequirement(200002, 1), new ArchetypeCardRequirement(100001, 10));
    }

    @Test void acceptsExactMinimumsAndMixedCatalogs() {
        DeckEntity entity = setup(Map.of(200001, 4, 200002, 1, 100001, 10), rules(), null);
        scheduler.updateDeckArchetype(1);
        assertEquals(1, entity.getDeckArchetypeId());
        verify(messageProducer).publishDeckSync("candidate");
    }

    @Test void rejectsAnyFailedRequirementAndClearsExistingAssignment() {
        DeckEntity entity = setup(Map.of(200001, 3, 200002, 1, 100001, 10), rules(), 1);
        scheduler.updateDeckArchetype(1);
        assertNull(entity.getDeckArchetypeId());
        verify(messageProducer).publishDeckSync("candidate");
    }

    @Test void missingAndConsideringCardsDoNotSatisfyRequirements() {
        DeckEntity entity = setup(Map.of(200001, 0, 100001, 10), rules(), 1);
        scheduler.deckArchetypeScheduler();
        assertNull(entity.getDeckArchetypeId());
    }

    @Test void emptyRequirementsPreserveSimilarityMatching() {
        DeckEntity entity = setup(Map.of(100001, 10), List.of(), null);
        scheduler.updateDeckArchetype(1);
        assertEquals(1, entity.getDeckArchetypeId());
    }

    @Test void requirementsCannotBypassSimilarityThreshold() {
        DeckEntity entity = setup(Map.of(200001, 4, 100099, 90), List.of(new ArchetypeCardRequirement(200001, 4)), 1);
        scheduler.updateDeckArchetype(1);
        assertNull(entity.getDeckArchetypeId());
    }

    @Test void reassignsToNextEligibleCandidate() {
        DeckEntity entity = setup(Map.of(100001, 10), rules(), 1);
        when(deckArchetypeRepository.findAll()).thenReturn(List.of(archetype(1, "reference", rules()), archetype(2, "other", List.of())));
        summary("other", Map.of(100001, 8, 100002, 2));
        scheduler.updateDeckArchetype(1);
        assertEquals(2, entity.getDeckArchetypeId());
    }

    @Test void unchangedDatabaseAssignmentDoesNotRepublishDespiteStaleSummary() {
        setup(Map.of(100001, 10), List.of(), 1);
        scheduler.updateDeckArchetype(1);
        verify(deckRepository, never()).saveAndFlush(any());
        verifyNoInteractions(messageProducer);
    }

    @Test void attributeRulesRejectMissingClanOrPoliticsAndAcceptAlternativeCards() {
        var entity = setup(Map.of(200001, 2, 200002, 2, 100001, 6, 100002, 4), List.of(), 1);
        var archetype = archetype(1, "reference", List.of(new ArchetypeCardRequirement(100001, 6)));
        archetype.setAttributeRequirements(List.of(
                new com.vtesdecks.model.ArchetypeAttributeRequirement(com.vtesdecks.model.ArchetypeAttributeRequirement.Type.CRYPT_CLAN, "Malkavian", 4),
                new com.vtesdecks.model.ArchetypeAttributeRequirement(com.vtesdecks.model.ArchetypeAttributeRequirement.Type.LIBRARY_TYPE, "Political Action", 10)));
        when(deckArchetypeRepository.findAll()).thenReturn(List.of(archetype));
        scheduler.updateDeckArchetype(1);
        assertNull(entity.getDeckArchetypeId());
        for (int id : List.of(200001, 200002)) {
            var crypt = mock(com.vtesdecks.cache.indexable.Crypt.class);
            when(crypt.getClan()).thenReturn("Malkavian");
            when(cryptCache.get(id)).thenReturn(crypt);
        }
        scheduler.updateDeckArchetype(1);
        assertNull(entity.getDeckArchetypeId());
        for (int id : List.of(100001, 100002)) {
            var library = mock(com.vtesdecks.cache.indexable.Library.class);
            when(library.getTypes()).thenReturn(java.util.Set.of("Political Action"));
            when(libraryCache.get(id)).thenReturn(library);
        }
        scheduler.updateDeckArchetype(1);
        assertEquals(1, entity.getDeckArchetypeId());
    }

    @Test void saveTriggeredFailurePropagates() {
        when(deckArchetypeRepository.findAll()).thenThrow(new RuntimeException("database failure"));
        assertThrows(RuntimeException.class, () -> scheduler.updateDeckArchetype(1));
    }
}
