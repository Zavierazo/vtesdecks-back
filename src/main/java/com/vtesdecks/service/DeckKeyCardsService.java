package com.vtesdecks.service;

import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.cache.indexable.deck.card.Card;
import com.vtesdecks.cache.redis.entity.ArchetypeKeyCard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class DeckKeyCardsService {

    /**
     * Threshold used by archetypes: card must appear in at least 10% of decks.
     */
    public static final double MIN_APPEARANCE_THRESHOLD = 0.1;

    /**
     * Threshold used for suggested cards: card must appear in at least 30% of similar decks.
     */
    public static final double SUGGESTED_CARDS_THRESHOLD = 0.3;

    /**
     * Given a list of decks, computes the key cards with their appearance rate,
     * average copies, min/max normalized counts and mode (most common copy count).
     * Only cards that appear in at least {@code threshold} fraction of the decks are included.
     *
     * @param decks     the decks to analyse
     * @param threshold minimum appearance rate (0.0–1.0) for a card to be included
     * @return list of key cards sorted by appearance rate descending, empty if no decks provided
     */
    public List<ArchetypeKeyCard> computeKeyCards(List<Deck> decks, double threshold) {
        // Map<cardId, Map<copies, deckCount>>
        Map<Integer, Map<Integer, Integer>> cardCopyDistribution = new HashMap<>();
        Map<Integer, Integer> cardDeckCount = new HashMap<>();
        long totalDecks = 0;

        for (Deck deck : decks) {
            totalDecks++;
            Set<Integer> seenInThisDeck = new HashSet<>();

            for (Card card : deck.getCrypt()) {
                accumulateCard(card, seenInThisDeck, cardCopyDistribution, cardDeckCount);
            }
            for (List<Card> cards : deck.getLibraryByType().values()) {
                for (Card card : cards) {
                    accumulateCard(card, seenInThisDeck, cardCopyDistribution, cardDeckCount);
                }
            }
        }

        if (totalDecks == 0) {
            return new ArrayList<>();
        }

        List<ArchetypeKeyCard> keyCards = new ArrayList<>();

        for (Map.Entry<Integer, Map<Integer, Integer>> entry : cardCopyDistribution.entrySet()) {
            int cardId = entry.getKey();
            Map<Integer, Integer> distribution = entry.getValue();
            int decksWithCard = cardDeckCount.getOrDefault(cardId, 0);
            double appearanceRate = (double) decksWithCard / totalDecks;

            if (appearanceRate < threshold) {
                continue;
            }

            // Average copies across decks that include the card
            double totalCopies = distribution.entrySet().stream()
                    .mapToDouble(e -> (double) e.getKey() * e.getValue())
                    .sum();
            double avg = totalCopies / decksWithCard;

            // Mode: most common number of copies
            int mode = distribution.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(1);

            // Normalized min/max: exclude copy counts used in fewer than max(10%, 1) decks
            double normThreshold = Math.max(decksWithCard * 0.1, 1.0);
            List<Integer> validCopyCounts = distribution.entrySet().stream()
                    .filter(e -> e.getValue() >= normThreshold)
                    .map(Map.Entry::getKey)
                    .sorted()
                    .collect(Collectors.toList());

            int min, max;
            if (validCopyCounts.isEmpty()) {
                min = mode;
                max = mode;
            } else {
                min = validCopyCounts.get(0);
                max = validCopyCounts.get(validCopyCounts.size() - 1);
            }

            keyCards.add(ArchetypeKeyCard.builder()
                    .id(cardId)
                    .appearanceRate(Math.round(appearanceRate * 10000.0) / 10000.0)
                    .avg(Math.round(avg * 100.0) / 100.0)
                    .min(min)
                    .max(max)
                    .number(mode)
                    .build());
        }

        // Sort all cards by appearanceRate descending
        keyCards.sort((a, b) -> Double.compare(b.getAppearanceRate(), a.getAppearanceRate()));
        return keyCards;
    }

    private void accumulateCard(Card card, Set<Integer> seenInThisDeck,
                                Map<Integer, Map<Integer, Integer>> cardCopyDistribution,
                                Map<Integer, Integer> cardDeckCount) {
        int cardId = card.getId();
        int copies = card.getNumber();
        cardCopyDistribution.computeIfAbsent(cardId, k -> new HashMap<>())
                .merge(copies, 1, Integer::sum);
        if (seenInThisDeck.add(cardId)) {
            cardDeckCount.merge(cardId, 1, Integer::sum);
        }
    }
}

