package com.vtesdecks.api.service;

import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.DeckIndex;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.cache.indexable.deck.card.Card;
import com.vtesdecks.model.DeckQuery;
import com.vtesdecks.model.api.ApiCard;
import com.vtesdecks.model.api.ApiShoppingOptimization;
import com.vtesdecks.model.api.ApiShoppingPrecon;
import com.vtesdecks.model.api.ApiShoppingSingleCard;
import com.vtesdecks.service.CurrencyExchangeService;
import com.vtesdecks.util.VtesUtils;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.vtesdecks.util.Constants.DEFAULT_CURRENCY;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApiShoppingOptimizerService {
    private final DeckIndex deckIndex;
    private final CryptCache cryptCache;
    private final LibraryCache libraryCache;
    private final CurrencyExchangeService currencyExchangeService;

    public ApiShoppingOptimization optimize(List<ApiCard> cards, String currencyCode) {
        Map<Integer, Integer> wanted = normalize(cards);
        Map<Integer, BigDecimal> singlePrices = getSinglePrices(wanted.keySet());
        Map<Integer, Integer> remaining = new TreeMap<>(wanted);
        List<PreconOption> precons = getPreconOptions();
        Map<String, Selection> selections = new LinkedHashMap<>();
        // Greedy heuristic: keep buying the precon whose covered cards are worth more than
        // the precon itself, always picking the one with the biggest saving. Each pick
        // strictly reduces the remaining quantities, so the loop always terminates.
        while (!remaining.isEmpty()) {
            PreconOption best = null;
            BigDecimal bestSavings = BigDecimal.ZERO;
            for (PreconOption precon : precons) {
                BigDecimal savings = coveredValue(precon, remaining, singlePrices).subtract(precon.getPrice());
                if (savings.compareTo(bestSavings) > 0) {
                    best = precon;
                    bestSavings = savings;
                }
            }
            if (best == null) {
                break;
            }
            PreconOption chosen = best;
            Selection selection = selections.computeIfAbsent(chosen.getDeck().getId(), id -> new Selection(chosen));
            selection.number++;
            for (Map.Entry<Integer, Integer> preconCard : best.getCards().entrySet()) {
                Integer cardId = preconCard.getKey();
                int covered = Math.min(remaining.getOrDefault(cardId, 0), preconCard.getValue());
                if (covered > 0) {
                    selection.coveredCards.merge(cardId, covered, Integer::sum);
                    int left = remaining.get(cardId) - covered;
                    if (left > 0) {
                        remaining.put(cardId, left);
                    } else {
                        remaining.remove(cardId);
                    }
                }
            }
        }
        return buildResult(wanted, remaining, selections, singlePrices, currencyCode);
    }

    private Map<Integer, Integer> normalize(List<ApiCard> cards) {
        Map<Integer, Integer> wanted = new TreeMap<>();
        List<Integer> unknown = new ArrayList<>();
        for (ApiCard card : cards) {
            if (card.getId() == null) {
                continue;
            }
            boolean exists = (VtesUtils.isCrypt(card.getId()) && cryptCache.get(card.getId()) != null)
                    || (VtesUtils.isLibrary(card.getId()) && libraryCache.get(card.getId()) != null);
            if (!exists) {
                unknown.add(card.getId());
                continue;
            }
            wanted.merge(card.getId(), card.getNumber() != null && card.getNumber() > 0 ? card.getNumber() : 1, Integer::sum);
        }
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unknown card ids: " + unknown);
        }
        return wanted;
    }

    private Map<Integer, BigDecimal> getSinglePrices(Iterable<Integer> cardIds) {
        Map<Integer, BigDecimal> prices = new HashMap<>();
        for (Integer cardId : cardIds) {
            BigDecimal minPrice = VtesUtils.isCrypt(cardId)
                    ? cryptCache.get(cardId).getMinPrice()
                    : libraryCache.get(cardId).getMinPrice();
            if (minPrice != null) {
                prices.put(cardId, minPrice);
            }
        }
        return prices;
    }

    private List<PreconOption> getPreconOptions() {
        List<PreconOption> options = new ArrayList<>();
        try (ResultSet<Deck> result = deckIndex.selectAll(DeckQuery.builder().type(DeckType.PRECONSTRUCTED).build())) {
            for (Deck deck : result) {
                BigDecimal price = deck.getStats() != null ? deck.getStats().getMsrp() : null;
                if (price == null) {
                    // Without a product price the precon can't compete against single cards
                    continue;
                }
                Map<Integer, Integer> cardCounts = new HashMap<>();
                for (Card card : deck.getCrypt()) {
                    cardCounts.merge(card.getId(), card.getNumber(), Integer::sum);
                }
                for (List<Card> libraryCards : deck.getLibraryByType().values()) {
                    for (Card card : libraryCards) {
                        cardCounts.merge(card.getId(), card.getNumber(), Integer::sum);
                    }
                }
                options.add(new PreconOption(deck, price, cardCounts));
            }
        }
        return options;
    }

    private BigDecimal coveredValue(PreconOption precon, Map<Integer, Integer> remaining, Map<Integer, BigDecimal> singlePrices) {
        BigDecimal value = BigDecimal.ZERO;
        for (Map.Entry<Integer, Integer> wantedCard : remaining.entrySet()) {
            Integer inPrecon = precon.getCards().get(wantedCard.getKey());
            BigDecimal price = singlePrices.get(wantedCard.getKey());
            if (inPrecon != null && price != null) {
                value = value.add(price.multiply(BigDecimal.valueOf(Math.min(wantedCard.getValue(), inPrecon))));
            }
        }
        return value;
    }

    private ApiShoppingOptimization buildResult(Map<Integer, Integer> wanted, Map<Integer, Integer> remaining,
                                                Map<String, Selection> selections, Map<Integer, BigDecimal> singlePrices,
                                                String currencyCode) {
        BigDecimal totalPrice = BigDecimal.ZERO;
        List<ApiShoppingPrecon> preconDecks = new ArrayList<>();
        for (Selection selection : selections.values()) {
            Deck deck = selection.precon.getDeck();
            BigDecimal unitPrice = convert(selection.precon.getPrice(), currencyCode);
            BigDecimal selectionTotal = unitPrice.multiply(BigDecimal.valueOf(selection.number));
            totalPrice = totalPrice.add(selectionTotal);
            List<ApiCard> coveredCards = new ArrayList<>();
            selection.coveredCards.forEach((cardId, number) -> {
                ApiCard apiCard = new ApiCard();
                apiCard.setId(cardId);
                apiCard.setNumber(number);
                coveredCards.add(apiCard);
            });
            preconDecks.add(ApiShoppingPrecon.builder()
                    .deckId(deck.getId())
                    .name(deck.getName())
                    .set(deck.getSet())
                    .number(selection.number)
                    .unitPrice(unitPrice)
                    .totalPrice(selectionTotal)
                    .coveredCards(coveredCards)
                    .build());
        }
        List<ApiShoppingSingleCard> singleCards = new ArrayList<>();
        for (Map.Entry<Integer, Integer> card : remaining.entrySet()) {
            BigDecimal price = singlePrices.get(card.getKey());
            BigDecimal unitPrice = price != null ? convert(price, currencyCode) : null;
            BigDecimal cardTotal = unitPrice != null ? unitPrice.multiply(BigDecimal.valueOf(card.getValue())) : null;
            if (cardTotal != null) {
                totalPrice = totalPrice.add(cardTotal);
            }
            singleCards.add(ApiShoppingSingleCard.builder()
                    .id(card.getKey())
                    .number(card.getValue())
                    .unitPrice(unitPrice)
                    .totalPrice(cardTotal)
                    .build());
        }
        BigDecimal singlesOnlyPrice = BigDecimal.ZERO;
        for (Map.Entry<Integer, Integer> card : wanted.entrySet()) {
            BigDecimal price = singlePrices.get(card.getKey());
            if (price != null) {
                singlesOnlyPrice = singlesOnlyPrice.add(convert(price, currencyCode).multiply(BigDecimal.valueOf(card.getValue())));
            }
        }
        return ApiShoppingOptimization.builder()
                .preconDecks(preconDecks)
                .singleCards(singleCards)
                .totalPrice(totalPrice)
                .singlesOnlyPrice(singlesOnlyPrice)
                .currency(currencyCode)
                .build();
    }

    private BigDecimal convert(BigDecimal price, String currencyCode) {
        return currencyExchangeService.convert(price, DEFAULT_CURRENCY, currencyCode);
    }

    @Value
    private static class PreconOption {
        Deck deck;
        BigDecimal price;
        Map<Integer, Integer> cards;
    }

    private static class Selection {
        private final PreconOption precon;
        private final Map<Integer, Integer> coveredCards = new TreeMap<>();
        private int number = 0;

        private Selection(PreconOption precon) {
            this.precon = precon;
        }
    }
}
