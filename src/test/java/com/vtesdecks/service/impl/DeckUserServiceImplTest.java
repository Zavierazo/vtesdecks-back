package com.vtesdecks.service.impl;

import com.vtesdecks.api.service.AchievementService;
import com.googlecode.cqengine.ConcurrentIndexedCollection;
import com.vtesdecks.cache.indexable.DeckSummary;
import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.jpa.entity.DeckUserEntity;
import com.vtesdecks.jpa.repositories.DeckRepository;
import com.vtesdecks.jpa.repositories.DeckUserRepository;
import com.vtesdecks.jpa.repositories.UserRepository;
import com.vtesdecks.messaging.MessageProducer;
import com.vtesdecks.model.DeckQuery;
import com.vtesdecks.service.DeckService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static com.googlecode.cqengine.query.QueryFactory.all;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeckUserServiceImplTest {
    @Mock
    private DeckRepository deckRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DeckUserRepository deckUserRepository;
    @Mock
    private DeckService deckService;
    @Mock
    private MessageProducer messageProducer;
    @Mock
    private AchievementService achievementService;
    @InjectMocks
    private DeckUserServiceImpl service;

    @Test
    void profileUpdatesRefreshOwnedAndLinkedTournamentDecks() {
        DeckSummary community = new DeckSummary();
        community.setId("community");
        var owned = new ConcurrentIndexedCollection<DeckSummary>();
        owned.add(community);
        DeckSummary tournament = new DeckSummary();
        tournament.setId("tournament");
        var linked = new ConcurrentIndexedCollection<DeckSummary>();
        linked.add(tournament);
        when(userRepository.findVerifiedVeknId(42)).thenReturn("003120065");
        when(deckService.getDecks(any())).thenReturn(owned.retrieve(all(DeckSummary.class)),
                linked.retrieve(all(DeckSummary.class)));

        service.refreshUserDecks(42);

        ArgumentCaptor<DeckQuery> queries = ArgumentCaptor.forClass(DeckQuery.class);
        verify(deckService, times(2)).getDecks(queries.capture());
        assertEquals(DeckType.USER, queries.getAllValues().get(0).getType());
        assertEquals(42, queries.getAllValues().get(0).getUserId());
        DeckQuery tournamentQuery = queries.getAllValues().get(1);
        assertEquals(DeckType.TOURNAMENT, tournamentQuery.getType());
        assertEquals("003120065", tournamentQuery.getVeknId());
        assertNull(tournamentQuery.getUserId());
        verify(messageProducer).publishDeckSync("community");
        verify(messageProducer).publishDeckSync("tournament");
    }

    @Test
    void doesNotRefreshTheArchiveWithoutAVerifiedVeknId() {
        service.refreshTournamentDecks(null);
        verifyNoInteractions(deckService, messageProducer);
    }

    @Test
    void rejectsOwnerRatingBeforeReadingOrWritingEngagement() {
        when(deckRepository.existsByIdAndUser("deck", 42)).thenReturn(true);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.rate(42, "deck", 5));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verifyNoInteractions(deckUserRepository, deckService, messageProducer, achievementService);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rejectsOwnerBookmarkChangesBeforeAnySideEffects(boolean favorite) {
        when(deckRepository.existsByIdAndUser("deck", 42)).thenReturn(true);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.favorite(42, "deck", favorite));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verifyNoInteractions(deckUserRepository, deckService, messageProducer, achievementService);
    }

    @Test
    void allowsRatingWhenUserDoesNotOwnDeck() {
        service.rate(42, "deck", 4);
        verify(deckRepository).existsByIdAndUser("deck", 42);
        ArgumentCaptor<DeckUserEntity> saved = ArgumentCaptor.forClass(DeckUserEntity.class);
        verify(deckUserRepository).save(saved.capture());
        assertEquals(4, saved.getValue().getRate());
        assertFalse(saved.getValue().getFavorite());
        verify(messageProducer).publishDeckSync("deck");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void allowsBookmarkChangesWhenUserDoesNotOwnDeck(boolean favorite) {
        DeckUserEntity existing = new DeckUserEntity();
        existing.setId(new DeckUserEntity.DeckUserId(42, "deck"));
        existing.setFavorite(!favorite);
        existing.setRate(4);
        when(deckUserRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        assertEquals(favorite, service.favorite(42, "deck", favorite));
        verify(deckRepository).existsByIdAndUser("deck", 42);
        verify(deckUserRepository).save(existing);
        assertEquals(favorite, existing.getFavorite());
        assertEquals(4, existing.getRate());
        verify(messageProducer).publishDeckSync("deck");
    }
}
