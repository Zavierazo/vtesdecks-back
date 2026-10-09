package com.vtesdecks.service;

import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.cache.DeckCardIndex;
import com.vtesdecks.cache.indexable.DeckSummary;
import com.vtesdecks.jpa.entity.DeckArchetypeEntity;
import com.vtesdecks.jpa.repositories.DeckArchetypeRepository;
import com.vtesdecks.model.ArchetypeCardRequirement;
import com.vtesdecks.model.DeckQuery;
import com.vtesdecks.util.CosineSimilarityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeckArchetypeSuggestionsTest {
    @Mock
    DeckCardIndex deckCardIndex;
    @Mock
    DeckArchetypeRepository repository;
    @Mock
    DeckService deckService;
    @InjectMocks
    DeckArchetypeService service;

    @BeforeEach
    void candidates() {
        DeckSummary candidate = summary("candidate", Map.of(100001, 10));
        DeckSummary other = summary("other", Map.of(100001, 10));
        when(candidate.getId()).thenReturn("candidate");
        when(other.getId()).thenReturn("other");
        when(candidate.getPlayers()).thenReturn(50);
        when(other.getPlayers()).thenReturn(20);
        when(candidate.getName()).thenReturn("Candidate");
        when(candidate.getDeckArchetypeId()).thenReturn(null);
        when(other.getDeckArchetypeId()).thenReturn(null);
        when(deckService.getDecks(any())).thenAnswer(invocation -> {
            DeckQuery query = invocation.getArgument(0);
            List<DeckSummary> decks = query.getArchetype() != null ? List.of(candidate) : List.of(candidate, other);
            @SuppressWarnings("unchecked")
            ResultSet<DeckSummary> result = mock(ResultSet.class);
            lenient().when(result.iterator()).thenAnswer(ignored -> decks.iterator());
            lenient().when(result.stream()).thenAnswer(ignored -> decks.stream());
            return result;
        });
    }

    private DeckSummary summary(String id, Map<Integer, Integer> cards) {
        DeckSummary summary = mock(DeckSummary.class);
        when(summary.getL2Norm()).thenReturn(CosineSimilarityUtils.computeL2Norm(cards));
        when(deckCardIndex.getCardCounts(id)).thenReturn(cards);
        return summary;
    }

    private void reference(String id, Map<Integer, Integer> cards) {
        DeckSummary deck = summary(id, cards);
        when(deckService.getSummary(id)).thenReturn(deck);
    }

    @Test
    void nearestUsesSecondaryReferenceEvenWhenRequirementsFail() {
        when(repository.findAll()).thenReturn(List.of(
                DeckArchetypeEntity.builder().id(1).name("Closer").deckId("primary").secondaryDeckId("secondary")
                        .cardRequirements(List.of(new ArchetypeCardRequirement(200001, 4))).build(),
                DeckArchetypeEntity.builder().id(2).name("Eligible").deckId("eligible").build()));
        reference("primary", Map.of(100002, 10));
        reference("secondary", Map.of(100001, 10));
        reference("eligible", Map.of(100001, 8, 100002, 2));

        var suggestions = service.getSuggestions();

        assertEquals(1, suggestions.size());
        assertEquals("candidate", suggestions.getFirst().getDeckId());
        var nearest = suggestions.getFirst().getNearestArchetype();
        assertEquals(1, nearest.id());
        assertEquals("Closer", nearest.name());
        assertEquals(1.0, nearest.similarity(), 0.00001);
        verify(repository, never()).save(any());
    }

    @Test
    void usesWinnerAsReferenceAndFinalistsForSimilarity() {
        when(repository.findAll()).thenReturn(List.of());

        service.getSuggestions();

        ArgumentCaptor<DeckQuery> queryCaptor = ArgumentCaptor.forClass(DeckQuery.class);
        verify(deckService, atLeast(2)).getDecks(queryCaptor.capture());
        DeckQuery referenceQuery = queryCaptor.getAllValues().stream()
                .filter(query -> Integer.valueOf(1).equals(query.getMinPosition())
                        && Integer.valueOf(1).equals(query.getMaxPosition()))
                .findFirst()
                .orElseThrow();

        assertEquals(1, referenceQuery.getMinPosition());
        assertEquals(1, referenceQuery.getMaxPosition());
    }

    @Test
    void returnsNearestBelowClassificationThresholdAndSkipsMissingReferences() {
        when(repository.findAll()).thenReturn(List.of(
                DeckArchetypeEntity.builder().id(1).deckId("missing").build(),
                DeckArchetypeEntity.builder().id(2).name("Weak match").deckId("weak").build()));
        reference("weak", Map.of(100001, 1, 100002, 10));
        when(deckService.getSummary("missing")).thenReturn(null);

        var nearest = service.getSuggestions().getFirst().getNearestArchetype();

        assertEquals(2, nearest.id());
        assertEquals(1 / Math.sqrt(101), nearest.similarity(), 0.00001);
    }

    @Test
    void noReferenceLeavesSuggestionWithoutNearestArchetype() {
        when(repository.findAll()).thenReturn(List.of(
                DeckArchetypeEntity.builder().id(1).deckId("missing").build(),
                DeckArchetypeEntity.builder().id(0).deckId("unclassified").build()));

        assertNull(service.getSuggestions().getFirst().getNearestArchetype());
        verify(deckService, never()).getSummary("unclassified");
    }

    @Test
    void equalScoresChooseLowestArchetypeId() {
        when(repository.findAll()).thenReturn(List.of(
                DeckArchetypeEntity.builder().id(2).deckId("second").build(),
                DeckArchetypeEntity.builder().id(1).deckId("first").build()));
        reference("first", Map.of(100002, 10));
        reference("second", Map.of(100003, 10));

        var nearest = service.getSuggestions().getFirst().getNearestArchetype();

        assertEquals(1, nearest.id());
        assertEquals(0, nearest.similarity());
    }
}
