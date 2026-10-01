package com.vtesdecks.api.service;

import com.vtesdecks.api.util.ApiUtils;
import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.jpa.entity.DeckEntity;
import com.vtesdecks.jpa.repositories.DeckRepository;
import com.vtesdecks.messaging.MessageProducer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiDeckBuilderManagementTest {
    @Mock DeckRepository decks;
    @Mock com.vtesdecks.jpa.repositories.DeckCardRepository cards;
    @Mock com.vtesdecks.jpa.repositories.UserRepository users;
    @Mock MessageProducer messages;
    @Mock ApiUserNotificationService notifications;
    @Mock AchievementService achievements;
    @Mock DeckValidation validator;
    @InjectMocks ApiDeckBuilderService service;

    private DeckEntity owned(String id) {
        DeckEntity deck = DeckEntity.builder().id(id).name("../Same / name").description("Keep description")
                .customTags(List.of("league")).type(DeckType.COMMUNITY).user(7).published(false).collection(true).build();
        when(decks.findById(id)).thenReturn(Optional.of(deck));
        return deck;
    }

    @Test void visibilityPreservesOtherFieldsAndUsesPublicationSideEffects() {
        DeckEntity deck = owned("a");
        when(validator.isValid(eq(deck.getName()), eq(deck.getExtra()), anyList())).thenReturn(true);
        try (var auth = mockStatic(ApiUtils.class)) {
            auth.when(ApiUtils::extractUserId).thenReturn(7);
            assertTrue(service.visibility("a", true));
            assertTrue(deck.getPublished());
            assertEquals("Keep description", deck.getDescription());
            assertEquals(List.of("league"), deck.getCustomTags());
            assertTrue(deck.getCollection());
            verify(notifications).deckUpdateNotifications(deck);
            assertTrue(service.visibility("a", false));
            verify(notifications).deckDeleteNotifications("a");
            verify(messages, times(2)).publishDeckSync("a");
            verify(achievements, times(2)).activity(7);
        }
    }

    @Test void visibilityRejectsDeletedAndForeignDecks() {
        owned("deleted").setDeleted(true); owned("foreign").setUser(8);
        try (var auth = mockStatic(ApiUtils.class)) {
            auth.when(ApiUtils::extractUserId).thenReturn(7);
            assertThrows(ResponseStatusException.class, () -> service.visibility("deleted", true));
            assertThrows(ResponseStatusException.class, () -> service.visibility("foreign", true));
            verifyNoInteractions(messages, notifications);
        }
    }

    @Test void deletionAndTrackerPublishDeckSync() {
        DeckEntity deck = owned("a");
        try (var auth = mockStatic(ApiUtils.class)) {
            auth.when(ApiUtils::extractUserId).thenReturn(7);
            assertTrue(service.updateCollectionTracker("a", false));
            assertFalse(deck.getCollection());
            assertTrue(service.deleteDeck("a", false));
            assertTrue(deck.getDeleted());
            verify(messages, times(2)).publishDeckSync("a");
        }
    }

    @Test void rejectedDeletionAndTrackerDoNotPublishDeckSync() {
        owned("foreign").setUser(8);
        try (var auth = mockStatic(ApiUtils.class)) {
            auth.when(ApiUtils::extractUserId).thenReturn(7);
            assertFalse(service.updateCollectionTracker("foreign", true));
            assertFalse(service.deleteDeck("foreign", false));
            verifyNoInteractions(messages);
        }
    }

    @Test void invalidDeckCannotBePublishedAndHasNoSideEffects() {
        owned("invalid");
        try (var auth = mockStatic(ApiUtils.class)) {
            auth.when(ApiUtils::extractUserId).thenReturn(7);
            assertFalse(service.canPublish("invalid"));
            var error = assertThrows(ResponseStatusException.class, () -> service.visibility("invalid", true));
            assertEquals(422, error.getStatusCode().value());
            verify(decks, never()).saveAndFlush(any());
            verifyNoInteractions(messages, notifications, achievements);
        }
    }
}
