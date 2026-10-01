package com.vtesdecks.api.service;

import com.googlecode.cqengine.ConcurrentIndexedCollection;
import com.vtesdecks.api.util.ApiUtils;
import com.vtesdecks.cache.indexable.DeckSummary;
import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.cache.redis.entity.DeckTags;
import com.vtesdecks.cache.redis.repositories.DeckTagsRepository;
import com.vtesdecks.jpa.entity.DeckEntity;
import com.vtesdecks.jpa.repositories.DeckRepository;
import com.vtesdecks.jpa.repositories.DeckCardRepository;
import com.vtesdecks.jpa.repositories.UserRepository;
import com.vtesdecks.messaging.MessageProducer;
import com.vtesdecks.model.api.ApiDeckBuilder;
import com.vtesdecks.model.DeckQuery;
import com.vtesdecks.service.DeckService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static com.googlecode.cqengine.query.QueryFactory.all;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomDeckTagsServiceTest {
    @Mock DeckRepository deckRepository;
    @Mock DeckCardRepository deckCardRepository;
    @Mock UserRepository userRepository;
    @Mock MessageProducer messageProducer;
    @Mock ApiUserNotificationService apiUserNotificationService;
    @Mock AchievementService achievementService;
    @Mock DeckService deckService;
    @Mock DeckTagsRepository deckTagsRepository;
    @InjectMocks ApiDeckBuilderService builder;
    @InjectMocks ApiDeckService api;

    @Test
    void olderClientsPreserveTagsAndExplicitEmptyClearsThem() {
        DeckEntity entity = DeckEntity.builder().id("owned").user(7).name("Deck")
                .type(DeckType.COMMUNITY).customTags(List.of("league")).build();
        when(deckRepository.findById("owned")).thenReturn(Optional.of(entity));
        when(deckCardRepository.findByIdDeckId("owned")).thenReturn(List.of());
        ApiDeckBuilder input = new ApiDeckBuilder();
        input.setId("owned");
        input.setName("Deck");
        input.setCards(new ArrayList<>());
        try (var auth = mockStatic(ApiUtils.class)) {
            auth.when(ApiUtils::extractUserId).thenReturn(7);
            assertEquals(List.of("league"), builder.storeDeck(input).getCustomTags());
            input.setCustomTags(List.of("test2", "league"));
            assertEquals(List.of("test2", "league"), builder.storeDeck(input).getCustomTags());
            input.setCustomTags(List.of());
            assertEquals(List.of(), builder.storeDeck(input).getCustomTags());
        }
        verify(messageProducer, times(3)).publishDeckSync("owned");
    }

    @Test
    void invalidTagsAndOtherUsersCannotModifyTheDeck() {
        ApiDeckBuilder input = new ApiDeckBuilder();
        input.setId("owned");
        input.setCustomTags(List.of("UPPER"));
        assertThrows(ResponseStatusException.class, () -> builder.storeDeck(input));
        verifyNoInteractions(deckRepository);
        DeckEntity entity = DeckEntity.builder().id("owned").user(7).build();
        when(deckRepository.findById("owned")).thenReturn(Optional.of(entity));
        input.setCustomTags(List.of("valid"));
        try (var auth = mockStatic(ApiUtils.class)) {
            auth.when(ApiUtils::extractUserId).thenReturn(8);
            assertThrows(IllegalArgumentException.class, () -> builder.storeDeck(input));
        }
        verify(deckRepository, never()).save(any());
    }

    @Test
    void suggestionsUseOwnerScopedIndexQueryAndKeepGenericTagsFirst() {
        when(deckTagsRepository.findById(DeckTags.CACHE_ID)).thenReturn(Optional.of(
                DeckTags.builder().id(DeckTags.CACHE_ID).tags(List.of("stealth")).build()));
        ConcurrentIndexedCollection<DeckSummary> indexed = new ConcurrentIndexedCollection<>();
        DeckSummary privateDeck = new DeckSummary();
        privateDeck.setId("private");
        privateDeck.setPublished(false);
        privateDeck.setCustomTags(List.of("league", "stealth"));
        indexed.add(privateDeck);
        when(deckService.getDecks(any())).thenAnswer(invocation -> indexed.retrieve(all(DeckSummary.class)));
        try (var auth = mockStatic(ApiUtils.class)) {
            auth.when(ApiUtils::extractUserId).thenReturn(7);
            assertEquals(List.of("stealth", "league"), api.getUserDeckTags());
            auth.when(ApiUtils::extractUserId).thenReturn(8);
            indexed.clear();
            assertEquals(List.of("stealth"), api.getUserDeckTags());
            auth.when(ApiUtils::extractUserId).thenReturn(null);
            assertThrows(ResponseStatusException.class, () -> api.getUserDeckTags());
        }
        verify(deckService).getDecks(argThat((DeckQuery query) -> query.getUserId().equals(7) && query.getType() == DeckType.USER));
        verify(deckService).getDecks(argThat((DeckQuery query) -> query.getUserId().equals(8) && query.getType() == DeckType.USER));
        verifyNoInteractions(deckRepository);
    }
}
