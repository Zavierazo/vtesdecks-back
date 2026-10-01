package com.vtesdecks.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.indexable.Crypt;
import com.vtesdecks.cache.indexable.Library;
import com.vtesdecks.jpa.entity.DeckEntity;
import com.vtesdecks.model.api.ApiCard;
import com.vtesdecks.jpa.repositories.LimitedFormatRepository;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeckValidationTest {
    List<ApiCard> cards;
    final CryptCache crypt = mock(CryptCache.class);
    final LibraryCache library = mock(LibraryCache.class);
    final ObjectMapper mapper = new ObjectMapper();
    final DeckValidation validator = new DeckValidation(crypt, library, mock(LimitedFormatRepository.class), mapper);
    final DeckEntity deck = DeckEntity.builder().id("deck").name("Valid name").build();

    ApiCard row(int id, int quantity) {
        ApiCard card = new ApiCard();
        card.setId(id);
        card.setNumber(quantity);
        return card;
    }
    Crypt crypt(int id, int group) {
        Crypt card = new Crypt(); card.setId(id); card.setGroup(group); card.setSets(List.of("V5:1"));
        when(crypt.get(id)).thenReturn(card); return card;
    }
    Library library() {
        Library card = new Library(); card.setId(100001); card.setSets(List.of("V5:1"));
        when(library.get(100001)).thenReturn(card); return card;
    }
    @Test void sizesUnknownAndBannedCards() {
        crypt(200001, 6); Library card = library();
        cards = List.of(row(200001, 12), row(100001, 60), row(999999, 0));
        assertTrue(validator.isValid(deck.getName(), deck.getExtra(), cards));
        card.setBanned("banned"); assertFalse(validator.isValid(deck.getName(), deck.getExtra(), cards)); card.setBanned(null);
        for (int count : List.of(0, 59, 91)) {
            cards = List.of(row(200001, 12), row(100001, count));
            assertFalse(validator.isValid(deck.getName(), deck.getExtra(), cards));
        }
        cards = List.of(row(200001, 11), row(100001, 60));
        assertFalse(validator.isValid(deck.getName(), deck.getExtra(), cards));
        cards = List.of(row(200099, 12), row(100001, 60));
        assertFalse(validator.isValid(deck.getName(), deck.getExtra(), cards));
    }
    @Test void groupsMustBeAdjacentAndAnyGroupIsIgnored() {
        crypt(200001, 5); Crypt second = crypt(200002, 6); library();
        cards = List.of(row(200001, 6), row(200002, 6), row(100001, 60));
        assertTrue(validator.isValid(deck.getName(), deck.getExtra(), cards)); second.setGroup(7); assertFalse(validator.isValid(deck.getName(), deck.getExtra(), cards));
        second.setGroup(0); assertTrue(validator.isValid(deck.getName(), deck.getExtra(), cards));
    }
    @Test void selectedLimitedFormatChangesSizesAndAllowedCards() throws Exception {
        crypt(200001, 6); library();
        deck.setExtra(mapper.readTree("{\"limitedFormat\":{\"minCrypt\":6,\"minLibrary\":40,\"maxLibrary\":50,\"sets\":{\"V5\":true},\"allowed\":{},\"banned\":{}}}"));
        cards = List.of(row(200001, 6), row(100001, 40));
        assertTrue(validator.isValid(deck.getName(), deck.getExtra(), cards));
        deck.setExtra(mapper.readTree("{\"limitedFormat\":{\"minCrypt\":6,\"minLibrary\":40,\"sets\":{\"Other\":true},\"allowed\":{},\"banned\":{}}}"));
        assertFalse(validator.isValid(deck.getName(), deck.getExtra(), cards));
    }
    @Test void builderValidationUsesSubmittedCardsAndSaveQuantitySemantics() {
        crypt(200001, 6); library();
        var cryptCard = new com.vtesdecks.model.api.ApiCard();
        cryptCard.setId(200001); cryptCard.setNumber(12);
        var libraryCard = new com.vtesdecks.model.api.ApiCard();
        libraryCard.setId(100001); libraryCard.setNumber(60);
        var ignored = new com.vtesdecks.model.api.ApiCard();
        ignored.setId(999999);
        assertTrue(validator.isValid(deck.getName(), deck.getExtra(), Arrays.asList(cryptCard, libraryCard, ignored, null)));
        var replacement = new com.vtesdecks.model.api.ApiCard();
        replacement.setId(100001); replacement.setNumber(59);
        assertFalse(validator.isValid(deck.getName(), deck.getExtra(), List.of(cryptCard, libraryCard, replacement)));
    }
}
