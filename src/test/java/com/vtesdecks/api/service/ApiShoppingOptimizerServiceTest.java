package com.vtesdecks.api.service;

import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.DeckIndex;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.indexable.Crypt;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.cache.indexable.Library;
import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.cache.indexable.deck.Stats;
import com.vtesdecks.cache.indexable.deck.card.Card;
import com.vtesdecks.model.api.ApiCard;
import com.vtesdecks.model.api.ApiShoppingOptimization;
import com.vtesdecks.service.CurrencyExchangeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ApiShoppingOptimizerServiceTest {
    private static final int CRYPT_ID = 200001;
    private static final int LIBRARY_ID = 100001;
    private static final int OTHER_LIBRARY_ID = 100002;
    private static final String CURRENCY = "EUR";

    @Mock
    private DeckIndex deckIndex;
    @Mock
    private CryptCache cryptCache;
    @Mock
    private LibraryCache libraryCache;
    @Mock
    private CurrencyExchangeService currencyExchangeService;
    @InjectMocks
    private ApiShoppingOptimizerService service;

    @BeforeEach
    public void setUp() {
        lenient().when(currencyExchangeService.convert(any(BigDecimal.class), anyString(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    public void shouldBuyPreconWhenCheaperThanSingles() {
        mockCrypt(CRYPT_ID, new BigDecimal("10.00"));
        mockLibrary(LIBRARY_ID, new BigDecimal("5.00"));
        // Precon costs 12, covers cards worth 20 as singles
        mockPrecons(precon("precon1", new BigDecimal("12.00"),
                Map.of(CRYPT_ID, 1, LIBRARY_ID, 2)));

        ApiShoppingOptimization result = service.optimize(
                List.of(card(CRYPT_ID, 1), card(LIBRARY_ID, 2)), CURRENCY);

        assertEquals(1, result.getPreconDecks().size());
        assertEquals("precon1", result.getPreconDecks().get(0).getDeckId());
        assertEquals(Integer.valueOf(1), result.getPreconDecks().get(0).getNumber());
        assertTrue(result.getSingleCards().isEmpty());
        assertEquals(0, new BigDecimal("12.00").compareTo(result.getTotalPrice()));
        assertEquals(0, new BigDecimal("20.00").compareTo(result.getSinglesOnlyPrice()));
    }

    @Test
    public void shouldBuySinglesWhenPreconIsMoreExpensive() {
        mockCrypt(CRYPT_ID, new BigDecimal("1.00"));
        mockLibrary(LIBRARY_ID, new BigDecimal("1.00"));
        mockPrecons(precon("precon1", new BigDecimal("50.00"),
                Map.of(CRYPT_ID, 1, LIBRARY_ID, 1)));

        ApiShoppingOptimization result = service.optimize(
                List.of(card(CRYPT_ID, 1), card(LIBRARY_ID, 1)), CURRENCY);

        assertTrue(result.getPreconDecks().isEmpty());
        assertEquals(2, result.getSingleCards().size());
        assertEquals(0, new BigDecimal("2.00").compareTo(result.getTotalPrice()));
    }

    @Test
    public void shouldBuyMultipleCopiesOfSamePreconAndRemainingSingles() {
        mockLibrary(LIBRARY_ID, new BigDecimal("4.00"));
        mockLibrary(OTHER_LIBRARY_ID, new BigDecimal("2.00"));
        // Each copy covers 2 wanted copies worth 8, costs 5
        mockPrecons(precon("precon1", new BigDecimal("5.00"), Map.of(LIBRARY_ID, 2)));

        ApiShoppingOptimization result = service.optimize(
                List.of(card(LIBRARY_ID, 4), card(OTHER_LIBRARY_ID, 1)), CURRENCY);

        assertEquals(1, result.getPreconDecks().size());
        assertEquals(Integer.valueOf(2), result.getPreconDecks().get(0).getNumber());
        assertEquals(1, result.getSingleCards().size());
        assertEquals(Integer.valueOf(OTHER_LIBRARY_ID), result.getSingleCards().get(0).getId());
        // 2 precons (10.00) + 1 single (2.00)
        assertEquals(0, new BigDecimal("12.00").compareTo(result.getTotalPrice()));
    }

    @Test
    public void shouldFailOnUnknownCard() {
        assertThrows(IllegalArgumentException.class,
                () -> service.optimize(List.of(card(999999, 1)), CURRENCY));
    }

    private void mockPrecons(Deck... decks) {
        @SuppressWarnings("unchecked")
        ResultSet<Deck> resultSet = mock(ResultSet.class);
        when(resultSet.iterator()).thenReturn(List.of(decks).iterator());
        when(deckIndex.selectAll(any())).thenReturn(resultSet);
    }

    private Deck precon(String id, BigDecimal msrp, Map<Integer, Integer> cards) {
        Deck deck = new Deck();
        deck.setId(id);
        deck.setName(id);
        deck.setType(DeckType.PRECONSTRUCTED);
        Stats stats = new Stats();
        stats.setMsrp(msrp);
        deck.setStats(stats);
        cards.forEach((cardId, number) -> {
            Card card = Card.builder().id(cardId).number(number).build();
            if (cardId >= 200000) {
                deck.getCrypt().add(card);
            } else {
                deck.getLibraryByType().computeIfAbsent("Master", k -> new ArrayList<>()).add(card);
            }
        });
        return deck;
    }

    private void mockCrypt(int id, BigDecimal minPrice) {
        Crypt crypt = new Crypt();
        crypt.setId(id);
        crypt.setMinPrice(minPrice);
        lenient().when(cryptCache.get(eq(id))).thenReturn(crypt);
    }

    private void mockLibrary(int id, BigDecimal minPrice) {
        Library library = new Library();
        library.setId(id);
        library.setMinPrice(minPrice);
        lenient().when(libraryCache.get(eq(id))).thenReturn(library);
    }

    private ApiCard card(int id, int number) {
        ApiCard card = new ApiCard();
        card.setId(id);
        card.setNumber(number);
        return card;
    }
}
