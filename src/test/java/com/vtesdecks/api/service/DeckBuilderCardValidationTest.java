package com.vtesdecks.api.service;

import com.vtesdecks.api.GlobalExceptionHandler;
import com.vtesdecks.api.controller.ApiDeckBuilderController;
import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.jpa.entity.DeckEntity;
import com.vtesdecks.jpa.repositories.CryptRepository;
import com.vtesdecks.jpa.repositories.DeckCardRepository;
import com.vtesdecks.jpa.repositories.DeckRepository;
import com.vtesdecks.jpa.repositories.LibraryRepository;
import com.vtesdecks.messaging.MessageProducer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class DeckBuilderCardValidationTest {
    @Mock DeckRepository decks;
    @Mock DeckCardRepository cards;
    @Mock CryptRepository crypt;
    @Mock LibraryRepository library;
    @Mock MessageProducer messages;
    @Mock ApiUserNotificationService notifications;
    @Mock AchievementService achievements;
    @InjectMocks ApiDeckBuilderService service;
    private MockMvc mvc;

    @BeforeEach void setup() {
        var controller = new ApiDeckBuilderController();
        ReflectionTestUtils.setField(controller, "deckBuilderService", service);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("7", null, List.of()));
    }

    @AfterEach void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private DeckEntity ownedDeck() {
        var deck = DeckEntity.builder().id("owned").user(7).type(DeckType.COMMUNITY)
                .name("Previous").published(false).build();
        when(decks.findById("owned")).thenReturn(Optional.of(deck));
        when(cards.findByIdDeckId("owned")).thenReturn(List.of());
        return deck;
    }

    @Test void rejectsCreationAndUpdatesOfPublicAndPrivateDecksBeforeAnySideEffects() throws Exception {
        when(library.selectExistingIds(any())).thenReturn(List.of(102385));
        for (boolean published : List.of(false, true)) {
            for (String identity : List.of("", "\"id\":\"owned\",")) {
                mvc.perform(post("/api/1.0/user/decks/builder").contentType(MediaType.APPLICATION_JSON)
                                .content("{" + identity + "\"name\":\"Draft\",\"published\":" + published
                                        + ",\"cards\":[{\"id\":102385,\"number\":1},{\"id\":150006,\"number\":0}]}"))
                        .andExpect(status().isBadRequest())
                        .andExpect(content().string(org.hamcrest.Matchers.containsString("Unknown card IDs: [150006]")));
            }
        }
        verifyNoInteractions(decks, cards, messages, notifications, achievements);
    }

    @Test void rejectsOldCryptIdsWithAReadableSupportMessage() throws Exception {
        mvc.perform(post("/api/1.0/user/decks/builder").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cards\":[{\"id\":250001,\"number\":1}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Unknown card IDs: [250001]")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("contact support")));
        verifyNoInteractions(decks, cards, messages, notifications, achievements);
    }

    @Test void checksBothCatalogsInBatchesAndReportsAllUnknownIds() throws Exception {
        when(library.selectExistingIds(any())).thenReturn(List.of(102385));
        when(crypt.selectExistingIds(any())).thenReturn(List.of(201792));

        mvc.perform(post("/api/1.0/user/decks/builder").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cards":[{"id":102385,"number":1},{"id":201792,"number":1},
                                {"id":150006,"number":0},{"id":250001,"number":1},{"id":999999,"number":1}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Unknown card IDs: [150006, 250001, 999999]")));

        verify(library, times(1)).selectExistingIds(any());
        verify(crypt, times(1)).selectExistingIds(any());
        verifyNoInteractions(decks, cards, messages, notifications, achievements);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[{\"id\":102385,\"number\":-1}]",
            "[{\"id\":102385}]",
            "[null,{\"id\":102385,\"number\":1}]",
            "[{\"id\":102385,\"number\":1},{\"id\":102385,\"number\":0}]"
    })
    void onlyChecksIdsWithoutValidatingQuantitiesOrDuplicates(String entries) throws Exception {
        when(library.selectExistingIds(List.of(102385))).thenReturn(List.of(102385));
        ownedDeck();

        mvc.perform(post("/api/1.0/user/decks/builder").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"owned\",\"name\":\"Draft\",\"cards\":" + entries + "}"))
                .andExpect(status().isOk());

        verify(library, times(1)).selectExistingIds(List.of(102385));
        verifyNoInteractions(crypt);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void acceptsIncompletePrivateDecksAndPreservesValidZeroQuantityCards(int quantity) throws Exception {
        when(library.selectExistingIds(any())).thenReturn(List.of(102385));
        var deck = ownedDeck();

        mvc.perform(post("/api/1.0/user/decks/builder").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"owned\",\"name\":\"Incomplete\",\"published\":false,"
                                + "\"cards\":[{\"id\":102385,\"number\":" + quantity + ",\"type\":\"Forged\"}]}"))
                .andExpect(status().isOk());

        var saved = org.mockito.ArgumentCaptor.forClass(com.vtesdecks.jpa.entity.DeckCardEntity.class);
        verify(cards).save(saved.capture());
        assertEquals(102385, saved.getValue().getId().getCardId());
        assertEquals(quantity, saved.getValue().getNumber());
        assertEquals("Incomplete", deck.getName());
        verify(messages).publishDeckSync("owned");
    }
}
