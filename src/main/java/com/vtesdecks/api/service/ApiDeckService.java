package com.vtesdecks.api.service;

import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.api.mapper.ApiDeckMapper;
import com.vtesdecks.api.util.ApiUtils;
import com.vtesdecks.cache.factory.DeckFactory;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.cache.indexable.deck.card.Card;
import com.vtesdecks.cache.redis.entity.DeckTags;
import com.vtesdecks.cache.redis.repositories.DeckTagsRepository;
import com.vtesdecks.jpa.repositories.DeckRepository;
import com.vtesdecks.model.ApiDeckType;
import com.vtesdecks.model.DeckQuery;
import com.vtesdecks.model.DeckSort;
import com.vtesdecks.model.DeckTag;
import com.vtesdecks.model.api.ApiDeck;
import com.vtesdecks.model.api.ApiDecks;
import com.vtesdecks.service.DeckService;
import com.vtesdecks.util.CosineSimilarityUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class ApiDeckService {
    @Autowired
    private DeckService deckService;
    @Autowired
    private ApiDeckMapper mapper;
    @Autowired
    private DeckRepository deckRepository;
    @Autowired
    private DeckFactory deckFactory;
    @Autowired
    private ApiCollectionService apiCollectionService;
    @Autowired
    private DeckTagsRepository deckTagsRepository;

    public ApiDeck getDeck(String deckId, boolean detailed, boolean collectionTracker, String currencyCode) {
        Deck deck = deckService.getDeck(deckId);
        if (deck == null) {
            return null;
        }
        if (detailed) {
            return mapper.map(deck, ApiUtils.extractUserId(), collectionTracker, currencyCode);
        } else {
            return mapper.mapSummary(deck, ApiUtils.extractUserId(), null, currencyCode);
        }
    }

    public List<String> getDeckTags() {
        return deckTagsRepository.findById(DeckTags.CACHE_ID)
                .map(DeckTags::getTags)
                .orElseGet(this::findAndCacheDeckTags);
    }

    public List<ApiDeck> getSpoilerDecks(String currencyCode) {
        DeckQuery deckQuery = DeckQuery.builder()
                .apiType(ApiDeckType.PRECONSTRUCTED)
                .order(DeckSort.MODIFIED)
                .tags(List.of(DeckTag.SPOILER.getTag()))
                .build();
        List<ApiDeck> spoilerDecks = getDecks(deckQuery, null, null, currencyCode, 0, Integer.MAX_VALUE)
                .getDecks();
        return spoilerDecks.isEmpty() ? null : spoilerDecks;
    }

    private List<String> findAndCacheDeckTags() {
        Set<String> existingTags = new HashSet<>();
        try (ResultSet<Deck> decks = deckService.getDecks(DeckQuery.builder().build())) {
            decks.stream()
                    .map(Deck::getTags)
                    .filter(Objects::nonNull)
                    .forEach(existingTags::addAll);
        }
        List<String> tags = existingTags.isEmpty()
                ? Collections.emptyList()
                : Arrays.stream(DeckTag.values())
                .filter(deckTag -> deckTag.getFromDate() == null || !deckTag.getFromDate().isAfter(LocalDate.now()))
                .map(DeckTag::getTag)
                .filter(existingTags::contains)
                .toList();
        deckTagsRepository.save(DeckTags.builder().id(DeckTags.CACHE_ID).tags(tags).build());
        return tags;
    }

    public ApiDecks getDecks(DeckQuery deckQuery, Integer collectionPercentage, String bySimilarity, String currencyCode, int offset, int limit) {
        ResultSet<Deck> decks = deckService.getDecks(deckQuery);
        ApiDecks apiDecks = new ApiDecks();
        apiDecks.setOffset(offset);
        apiDecks.setCurrency(currencyCode);

        Stream<Deck> deckStream = decks.stream();
        boolean filteredInMemory = false;
        // Filter by collection percentage
        if (collectionPercentage != null && collectionPercentage > 0) {
            Map<Integer, Integer> collectionMap = apiCollectionService.getCollectionCardsMap();
            deckStream = deckStream.filter(deck -> matchCollectionPercentage(deck, collectionMap, collectionPercentage));
            filteredInMemory = true;
        }
        // Sort by similarity if requested
        if (bySimilarity != null) {
            Deck queryDeck = deckService.getDeck(bySimilarity);
            if (queryDeck != null) {
                Map<Integer, Integer> queryVector = CosineSimilarityUtils.getVector(queryDeck);
                deckStream = deckStream
                        .filter(target -> !target.getId().equals(bySimilarity))
                        .map(target -> Pair.of(target, CosineSimilarityUtils.cosineSimilarity(queryDeck, queryVector, target, CosineSimilarityUtils.getVector(target))))
                        .filter(pair -> pair.getValue() >= 0.5)
                        .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                        .map(Pair::getKey);
                filteredInMemory = true;
            }
        }
        if (filteredInMemory) {
            List<Deck> filteredDecks = deckStream.toList();
            apiDecks.setTotal(filteredDecks.size());
            deckStream = filteredDecks.stream();
        } else {
            apiDecks.setTotal(decks.size());
        }
        apiDecks.setDecks(deckStream
                .skip(offset)
                .limit(limit)
                .map(deck -> mapper.mapSummary(deck, ApiUtils.extractUserId(), deckQuery.getCards(), currencyCode))
                .toList());
        if (offset == 0 && deckQuery.getUserId() != null && deckQuery.getType() == DeckType.USER) {
            apiDecks.setRestorableDecks(deckRepository.selectUserDeleted(deckQuery.getUserId()).stream()
                    .map(dbDeck -> deckFactory.getDeck(dbDeck, new ArrayList<>(), new ArrayList<>()))
                    .map(deck -> mapper.mapSummary(deck, ApiUtils.extractUserId(), deckQuery.getCards(), currencyCode))
                    .toList());
        }
        return apiDecks;
    }

    private boolean matchCollectionPercentage(Deck deck, Map<Integer, Integer> collectionMap, Integer collectionPercentage) {
        int totalCards = 0;
        int collectionCards = 0;
        if (deck.getCrypt() != null) {
            for (Card card : deck.getCrypt()) {
                if (card.getNumber() != null && card.getNumber() > 0) {
                    totalCards += card.getNumber();
                    if (collectionMap.containsKey(card.getId())) {
                        collectionCards += Math.min(card.getNumber(), collectionMap.get(card.getId()));
                    }
                }
            }
        }
        if (deck.getLibraryByType() != null) {
            for (List<Card> cards : deck.getLibraryByType().values()) {
                if (cards != null) {
                    for (Card card : cards) {
                        if (card.getNumber() != null && card.getNumber() > 0) {
                            totalCards += card.getNumber();
                            if (collectionMap.containsKey(card.getId())) {
                                collectionCards += Math.min(card.getNumber(), collectionMap.get(card.getId()));
                            }
                        }
                    }
                }
            }
        }
        return totalCards > 0 && (collectionCards * 100 / totalCards) >= collectionPercentage;
    }
}
