package com.vtesdecks.cache.factory;

import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.cache.indexable.deck.card.Card;
import com.vtesdecks.cache.redis.entity.ArchetypeKeyCard;
import com.vtesdecks.cache.redis.entity.DeckArchetype;
import com.vtesdecks.jpa.entity.DeckArchetypeEntity;
import com.vtesdecks.model.DeckQuery;
import com.vtesdecks.service.DeckService;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Mapper(componentModel = "spring")
public abstract class DeckArchetypeFactory {


    private static final double MIN_APPEARANCE_THRESHOLD = 0.2;

    @Autowired
    private DeckService deckService;

    public abstract DeckArchetype getDeckArchetype(DeckArchetypeEntity deckArchetypeEntity);

    @AfterMapping
    protected void afterMapping(@MappingTarget DeckArchetype deckArchetype, DeckArchetypeEntity entity) {
        deckArchetype.setDeckCount(deckCount(DeckQuery.builder().archetype(entity.getId()).build()));
        deckArchetype.setTournamentCount(deckCount(DeckQuery.builder().archetype(entity.getId()).type(DeckType.TOURNAMENT).build()));
        deckArchetype.setTournament90Count(deckCount(DeckQuery.builder().archetype(entity.getId()).type(DeckType.TOURNAMENT).creationDate(LocalDate.now().minusDays(90)).build()));
        deckArchetype.setTournament180Count(deckCount(DeckQuery.builder().archetype(entity.getId()).type(DeckType.TOURNAMENT).creationDate(LocalDate.now().minusDays(180)).build()));
        deckArchetype.setTournament365Count(deckCount(DeckQuery.builder().archetype(entity.getId()).type(DeckType.TOURNAMENT).creationDate(LocalDate.now().minusDays(365)).build()));
        deckArchetype.setTournament730Count(deckCount(DeckQuery.builder().archetype(entity.getId()).type(DeckType.TOURNAMENT).creationDate(LocalDate.now().minusDays(730)).build()));
        if (entity.getDeckId() != null) {
            Deck deck = deckService.getDeck(entity.getDeckId());
            if (deck != null && deck.getStats() != null) {
                deckArchetype.setPrice(deck.getStats().getPrice());
                deckArchetype.setCurrency(deck.getStats().getCurrency());
            }
        }
        computeKeyCards(deckArchetype, entity);
    }

    private void computeKeyCards(DeckArchetype deckArchetype, DeckArchetypeEntity entity) {
        if (entity.getId() == null || entity.getId() == 0) {
            return;
        }

        // Map<cardId, Map<copies, deckCount>>
        Map<Integer, Map<Integer, Integer>> cardCopyDistribution = new HashMap<>();
        Map<Integer, Integer> cardDeckCount = new HashMap<>();
        long totalDecks = 0;

        try (ResultSet<Deck> deckResultSet = deckService.getDecks(
                DeckQuery.builder().archetype(entity.getId()).type(DeckType.TOURNAMENT).build())) {
            for (Deck deck : deckResultSet) {
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
        }

        if (totalDecks == 0) {
            return;
        }

        List<ArchetypeKeyCard> keyCards = new ArrayList<>();

        for (Map.Entry<Integer, Map<Integer, Integer>> entry : cardCopyDistribution.entrySet()) {
            int cardId = entry.getKey();
            Map<Integer, Integer> distribution = entry.getValue();
            int decksWithCard = cardDeckCount.getOrDefault(cardId, 0);
            double appearanceRate = (double) decksWithCard / totalDecks;

            if (appearanceRate < MIN_APPEARANCE_THRESHOLD) {
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
        deckArchetype.setKeyCards(keyCards.isEmpty() ? null : keyCards);
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

    private long deckCount(DeckQuery query) {
        try (ResultSet<Deck> deckResultSet = deckService.getDecks(query)) {
            return deckResultSet.stream().count();
        }
    }
}
