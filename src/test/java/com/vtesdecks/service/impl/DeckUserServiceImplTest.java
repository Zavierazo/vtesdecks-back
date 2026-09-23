package com.vtesdecks.service.impl;

import com.vtesdecks.api.service.AchievementService;
import com.vtesdecks.jpa.entity.DeckUserEntity;
import com.vtesdecks.jpa.repositories.DeckRepository;
import com.vtesdecks.jpa.repositories.DeckUserRepository;
import com.vtesdecks.messaging.MessageProducer;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeckUserServiceImplTest {
    @Mock
    private DeckRepository deckRepository;
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
