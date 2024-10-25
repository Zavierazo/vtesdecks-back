package com.vtesdecks.cache;

import com.googlecode.cqengine.ConcurrentIndexedCollection;
import com.googlecode.cqengine.IndexedCollection;
import com.googlecode.cqengine.index.hash.HashIndex;
import com.googlecode.cqengine.index.unique.UniqueIndex;
import com.googlecode.cqengine.query.Query;
import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.cache.indexable.DeckCard;
import com.vtesdecks.db.DeckCardMapper;
import com.vtesdecks.db.model.DbDeckCard;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.googlecode.cqengine.query.QueryFactory.equal;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DeckCardIndex {
    @Autowired
    private DeckCardMapper deckCardMapper;
    private IndexedCollection<DeckCard> cache = new ConcurrentIndexedCollection<>();

    @PostConstruct
    public void setUp() {
        cache.addIndex(UniqueIndex.onAttribute(DeckCard.ID_ATTRIBUTE));
        cache.addIndex(HashIndex.onAttribute(DeckCard.DECK_ID_ATTRIBUTE));
        cache.addIndex(HashIndex.onAttribute(DeckCard.CARD_ID_ATTRIBUTE));
        cache.addIndex(HashIndex.onAttribute(DeckCard.NUMBER_ATTRIBUTE));
        cache.addIndex(HashIndex.onAttribute(DeckCard.IS_CRYPT_ATTRIBUTE));
        cache.addIndex(HashIndex.onAttribute(DeckCard.IS_LIBRARY_ATTRIBUTE));
    }

    public IndexedCollection<DeckCard> getRepository() {
        return cache;
    }

    public List<DeckCard> refreshIndex(String deckId) {
        List<DeckCard> deckCards = new ArrayList<>();
        try {
            List<DbDeckCard> dbDeckCards = deckCardMapper.selectByDeck(deckId);
            for (DbDeckCard dbDeckCard : dbDeckCards) {
                deckCards.add(DeckCard.builder()
                        .deckId(dbDeckCard.getDeckId())
                        .id(dbDeckCard.getId())
                        .number(dbDeckCard.getNumber())
                        .build());
            }
            refreshIndex(deckId, deckCards);
        } catch (Exception e) {
            log.error("Error when refresh cardDeck {}: {}", deckId, deckCards, e);
        }
        return deckCards;
    }

    private synchronized void refreshIndex(String deckId, List<DeckCard> deckCards) {
        List<DeckCard> objectsToRemove = new ArrayList<>();
        List<DeckCard> objectsToUpdate = new ArrayList<>();
        List<DeckCard> objectsToAdd = new ArrayList<>();
        for (DeckCard deckCard : deckCards) {
            DeckCard oldDeckCard = get(deckCard.getUniqueId());
            if (oldDeckCard == null) {
                objectsToAdd.add(deckCard);
            } else if (!oldDeckCard.equals(deckCard)) {
                objectsToRemove.add(oldDeckCard);
                objectsToUpdate.add(deckCard);
            }
        }
        for (DeckCard oldDeckCard : getByDeckId(deckId)) {
            if (deckCards.stream().noneMatch(deckCard -> oldDeckCard.getId().equals(deckCard.getId()))) {
                objectsToRemove.add(oldDeckCard);
            }

        }
        if (isNotEmpty(objectsToAdd)) {
            cache.addAll(objectsToAdd);
        }
        if (isNotEmpty(objectsToRemove) || isNotEmpty(objectsToUpdate)) {
            cache.update(objectsToRemove, objectsToUpdate);
        }
    }


    public DeckCard get(String key) {
        Query<DeckCard> findByKeyQuery = equal(DeckCard.ID_ATTRIBUTE, key);
        ResultSet<DeckCard> result = cache.retrieve(findByKeyQuery);
        return (result.size() >= 1) ? result.uniqueResult() : null;
    }

    public List<DeckCard> getByDeckId(String deckId) {
        Query<DeckCard> findByKeyQuery = equal(DeckCard.DECK_ID_ATTRIBUTE, deckId);
        ResultSet<DeckCard> result = cache.retrieve(findByKeyQuery);
        return (result.size() >= 1) ? result.stream().collect(Collectors.toList()) : Collections.emptyList();
    }


    public void removeDeck(String deckId) {
        Query<DeckCard> findByDeckIdQuery = equal(DeckCard.DECK_ID_ATTRIBUTE, deckId);
        ResultSet<DeckCard> result = cache.retrieve(findByDeckIdQuery);
        for (DeckCard deckCard : result) {
            cache.remove(deckCard);
        }
    }
}
