package com.vtesdecks.api.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CardSearchUtilsTest {

    @Test
    public void shouldConvertFiltersKeepingNativeJsonTypes() {
        Map<String, Object> filters = new HashMap<>();
        filters.put("binderId", 3);
        filters.put("cardName", "gov");
        filters.put("priority", "HIGH");
        filters.put("cardTypes", List.of("Action", "Ally"));
        filters.put("empty", "");
        filters.put("nullValue", null);

        Map<String, String> params = CardSearchUtils.toParams(filters);

        assertEquals("3", params.get("binderId"));
        assertEquals("gov", params.get("cardName"));
        assertEquals("HIGH", params.get("priority"));
        assertEquals("Action,Ally", params.get("cardTypes"));
        assertFalse(params.containsKey("empty"));
        assertFalse(params.containsKey("nullValue"));
    }

    @Test
    public void shouldReturnEmptyParamsWhenFiltersAreNull() {
        assertTrue(CardSearchUtils.toParams(null).isEmpty());
    }

    @Test
    public void shouldNotRestrictWhenCardIdsAreNull() {
        Map<String, String> filters = new HashMap<>(Map.of("set", "V5"));

        assertTrue(CardSearchUtils.applyCardIds(filters, null));

        assertNull(filters.get("cardId"));
    }

    @Test
    public void shouldReturnEmptyResultWhenCardIdsAreEmpty() {
        Map<String, String> filters = new HashMap<>();

        assertFalse(CardSearchUtils.applyCardIds(filters, List.of()));
    }

    @Test
    public void shouldAddCardIdsAsFilter() {
        Map<String, String> filters = new HashMap<>();

        assertTrue(CardSearchUtils.applyCardIds(filters, List.of(200011, 200013)));

        assertEquals("200011,200013", filters.get("cardId"));
    }

    @Test
    public void shouldIntersectWithExistingCardIdFilter() {
        Map<String, String> filters = new HashMap<>(Map.of("cardId", "200011,100001"));

        assertTrue(CardSearchUtils.applyCardIds(filters, List.of(200011, 200013)));

        assertEquals("200011", filters.get("cardId"));
    }

    @Test
    public void shouldReturnEmptyResultWhenIntersectionIsEmpty() {
        Map<String, String> filters = new HashMap<>(Map.of("cardId", "100001"));

        assertFalse(CardSearchUtils.applyCardIds(filters, List.of(200011)));
    }

    @Test
    public void shouldReturnEmptyResultWhenExistingCardIdFilterIsEmpty() {
        Map<String, String> filters = new HashMap<>(Map.of("cardId", ""));

        assertFalse(CardSearchUtils.applyCardIds(filters, List.of(200011)));
    }

    @Test
    public void shouldRejectOversizedCardIdList() {
        Map<String, String> filters = new HashMap<>();
        List<Integer> cardIds = IntStream.rangeClosed(1, CardSearchUtils.MAX_CARD_IDS + 1).boxed().toList();

        assertThrows(IllegalArgumentException.class, () -> CardSearchUtils.applyCardIds(filters, cardIds));
    }

    @Test
    public void shouldAcceptMaximumSizedCardIdList() {
        Map<String, String> filters = new HashMap<>();
        List<Integer> cardIds = IntStream.rangeClosed(1, CardSearchUtils.MAX_CARD_IDS).boxed().toList();

        assertTrue(CardSearchUtils.applyCardIds(filters, cardIds));
    }
}
