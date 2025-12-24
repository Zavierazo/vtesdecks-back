package com.vtesdecks.api.service;

import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.api.mapper.ApiDeckMapper;
import com.vtesdecks.api.util.ApiUtils;
import com.vtesdecks.cache.factory.DeckFactory;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.cache.indexable.deck.card.Card;
import com.vtesdecks.jpa.repositories.DeckRepository;
import com.vtesdecks.model.DeckSort;
import com.vtesdecks.model.DeckType;
import com.vtesdecks.model.api.ApiDeck;
import com.vtesdecks.model.api.ApiDecks;
import com.vtesdecks.service.DeckService;
import com.vtesdecks.service.UserVisitService;
import com.vtesdecks.util.CosineSimilarityUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ApiDeckService {
    private final DeckService deckService;
    private final ApiDeckMapper mapper;
    private final DeckRepository deckRepository;
    private final DeckFactory deckFactory;
    private final ApiCollectionService apiCollectionService;
    private final UserVisitService userVisitService;

    public ApiDeck getDeck(String deckId, boolean detailed, boolean collectionTracker, String currencyCode) {
        Deck deck = deckService.getDeck(deckId);
        if (deck == null) {
            return null;
        }
        if (detailed) {
            return mapper.map(deck, ApiUtils.extractUserId(), collectionTracker, currencyCode);
        } else {
            return mapper.mapSummary(deck, ApiUtils.extractUserId(), null, null, currencyCode);
        }
    }

    public ApiDecks getDecks(DeckType type,
                             DeckSort order,
                             Integer userId,
                             String name,
                             String author,
                             Boolean exactAuthor,
                             String cardText,
                             List<String> clans,
                             List<String> disciplines,
                             List<String> cards,
                             List<Integer> cryptSize,
                             List<Integer> librarySize,
                             List<Integer> group,
                             Boolean starVampire,
                             Boolean singleClan,
                             Boolean singleDiscipline,
                             List<Integer> year,
                             List<Integer> players,
                             String master,
                             String action,
                             String political,
                             String retainer,
                             String equipment,
                             String ally,
                             String modifier,
                             String combat,
                             String reaction,
                             String event,
                             Boolean absoluteProportion,
                             List<String> tags,
                             String limitedFormat,
                             List<String> paths,
                             String bySimilarity,
                             Integer collectionPercentage,
                             Boolean favorite,
                             String currencyCode,
                             Integer offset,
                             Integer limit) {
        final Map<Integer, Integer> cardMap = new HashMap<>();
        if (cards != null && !cards.isEmpty()) {
            for (String card : cards) {
                int indexEqual = card.indexOf('=');
                int number = 1;
                int id;
                if (indexEqual > 0) {
                    id = Integer.parseInt(card.substring(0, indexEqual));
                    number = Integer.parseInt(card.substring(indexEqual + 1));
                } else {
                    id = Integer.parseInt(card);
                }
                cardMap.put(id, number);
            }
        }
        ResultSet<Deck> decks = deckService.getDecks(type, order, userId, name, author, exactAuthor, cardText, clans, disciplines,
                cardMap, cryptSize, librarySize, group, starVampire, singleClan, singleDiscipline, year, players, master, action, political, retainer,
                equipment, ally, modifier, combat, reaction, event, absoluteProportion, tags, limitedFormat, paths, favorite);
        ApiDecks apiDecks = new ApiDecks();
        apiDecks.setTotal(decks.size());
        apiDecks.setOffset(offset);

        Stream<Deck> deckStream = decks.stream();
        // Filter by collection percentage
        if (collectionPercentage != null && collectionPercentage > 0) {
            Map<Integer, Integer> collectionMap = apiCollectionService.getCollectionCardsMap();
            deckStream = deckStream.filter(deck -> matchCollectionPercentage(deck, collectionMap, collectionPercentage));
        }
        // Sort by similarity if requested
        if (bySimilarity != null) {
            Deck queryDeck = deckService.getDeck(bySimilarity);
            if (queryDeck != null) {
                Map<Integer, Integer> queryVector = CosineSimilarityUtils.getVector(queryDeck);
                deckStream = deckStream
                        .filter(target -> !target.getId().equals(bySimilarity))
                        .map(target -> Pair.of(target, CosineSimilarityUtils.cosineSimilarity(queryDeck, queryVector, target, CosineSimilarityUtils.getVector(target))))
                        .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                        .map(Pair::getKey);
            }
        }
        LocalDate lastVisit = userVisitService.getLastVisit(ApiUtils.extractUserId());
        apiDecks.setDecks(deckStream
                .skip(offset)
                .limit(limit)
                .map(deck -> mapper.mapSummary(deck, ApiUtils.extractUserId(), lastVisit, cardMap, currencyCode))
                .toList());
        if (offset == 0 && userId != null && type == DeckType.USER) {
            apiDecks.setRestorableDecks(deckRepository.selectUserDeleted(userId).stream()
                    .map(dbDeck -> deckFactory.getDeck(dbDeck, new ArrayList<>(), new ArrayList<>()))
                    .map(deck -> mapper.mapSummary(deck, ApiUtils.extractUserId(), lastVisit, cardMap, currencyCode))
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
