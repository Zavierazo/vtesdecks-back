package com.vtesdecks.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Iterables;
import com.vtesdecks.api.util.ApiUtils;
import com.vtesdecks.cache.DeckIndex;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.indexable.Library;
import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.db.DeckCardMapper;
import com.vtesdecks.db.DeckMapper;
import com.vtesdecks.db.UserMapper;
import com.vtesdecks.db.model.DbDeck;
import com.vtesdecks.db.model.DbDeckCard;
import com.vtesdecks.db.model.DbUser;
import com.vtesdecks.integration.KRCGClient;
import com.vtesdecks.model.ImportType;
import com.vtesdecks.model.api.ApiCard;
import com.vtesdecks.model.api.ApiDeckBuilder;
import com.vtesdecks.model.krcg.Card;
import com.vtesdecks.model.krcg.Deck;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

@Slf4j
@Service
public class ApiDeckBuilderService {

    @Autowired
    private DeckMapper deckMapper;
    @Autowired
    private DeckCardMapper deckCardMapper;
    @Autowired
    private LibraryCache libraryCache;
    @Autowired
    private KRCGClient krcgClient;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private DeckIndex deckIndex;


    public ApiDeckBuilder getDeck(String deckId) {
        Integer userId = ApiUtils.extractUserId();
        DbDeck deck = deckMapper.selectById(deckId);
        if (deck == null || !isOwnerOrAdmin(deck, userId) || deck.isDeleted()) {
            return null;
        }
        ApiDeckBuilder deckBuilder = new ApiDeckBuilder();
        deckBuilder.setId(deck.getId());
        deckBuilder.setName(deck.getName());
        deckBuilder.setDescription(deck.getDescription());
        deckBuilder.setPublished(deck.isPublished());
        deckBuilder.setCollection(deck.isCollection());
        deckBuilder.setCards(new ArrayList<>());
        deckBuilder.setExtra(deck.getExtra());
        List<DbDeckCard> dbDeckCards = deckCardMapper.selectByDeck(deck.getId());
        for (DbDeckCard deckCard : dbDeckCards) {
            ApiCard apiCard = getApiCard(deckCard.getId(), deckCard.getNumber());
            deckBuilder.getCards().add(apiCard);
        }
        return deckBuilder;
    }


    public ApiDeckBuilder importDeck(String text, ImportType type) {
        switch (type) {
            case AMARANTH:
                log.info("Importing amaranth deck with url {}", text);
                return importDeck(text, krcgClient::getAmaranthDeck);
            case VDB:
                log.info("Importing vdb deck with url {}", text);
                return importDeck(text, krcgClient::getVDBDeck);
        }
        return null;
    }

    public ApiDeckBuilder storeDeck(ApiDeckBuilder apiDeckBuilder) {
        Integer userId = ApiUtils.extractUserId();
        DbDeck deck = null;
        if (apiDeckBuilder.getId() != null) {
            deck = deckMapper.selectById(apiDeckBuilder.getId());
        }
        String deckId = apiDeckBuilder.getId();
        if (deckId == null) {
            DbUser user = userMapper.selectById(userId);
            deckId = "user-" + StringUtils.lowerCase(user.getUsername()) + "-" + UUID.randomUUID().toString().replace("-", "");
        }
        boolean exists = true;
        if (deck == null) {
            deck = new DbDeck();
            deck.setId(deckId);
            deck.setType(DeckType.COMMUNITY);
            deck.setUser(userId);
            deck.setVerified(true);
            deck.setCreationDate(LocalDateTime.now());
            exists = false;
        } else if (!isOwnerOrAdmin(deck, userId)) {
            log.warn("Deck {} is not valid for user {}", apiDeckBuilder.getId(), userId);
            throw new IllegalArgumentException("Deck " + apiDeckBuilder.getId() + " is not valid for user " + userId);
        }
        deck.setName(apiDeckBuilder.getName());
        deck.setDescription(apiDeckBuilder.getDescription());
        deck.setPublished(apiDeckBuilder.isPublished());
        if (deck.getType() == DeckType.COMMUNITY) {
            deck.setExtra(apiDeckBuilder.getExtra());
            deck.setCollection(apiDeckBuilder.isCollection());
        }
        if (!exists) {
            deckMapper.insert(deck);
        } else {
            deckMapper.update(deck);
        }
        //Deck cards
        List<ApiCard> deckCards = apiDeckBuilder.getCards();
        Iterables.removeIf(deckCards, Objects::isNull);
        List<DbDeckCard> dbCards = deckCardMapper.selectByDeck(deck.getId());
        for (ApiCard card : deckCards) {
            if (card.getNumber() != null && card.getNumber() > 0) {
                DbDeckCard dbCard = dbCards.stream().filter(db -> db.getId().equals(card.getId())).findFirst().orElse(null);
                if (dbCard == null) {
                    dbCard = new DbDeckCard();
                    dbCard.setId(card.getId());
                    dbCard.setNumber(card.getNumber());
                    dbCard.setDeckId(deck.getId());
                    deckCardMapper.insert(dbCard);
                } else if (!dbCard.getNumber().equals(card.getNumber())) {
                    dbCard.setNumber(card.getNumber());
                    deckCardMapper.update(dbCard);
                }
            }
        }
        //Delete removed cards
        for (DbDeckCard card : dbCards) {
            try {
                Integer deckCard = deckCards.stream().map(ApiCard::getId).filter(id -> id.equals(card.getId())).findFirst().orElse(null);
                if (deckCard == null) {
                    deckCardMapper.delete(card.getDeckId(), card.getId());
                }
            } catch (Exception e) {
                log.error("Unable to delete card {}", card, e);
            }
        }
        //Enqueue indexation of new deck
        deckIndex.enqueueRefreshIndex(deck.getId());
        return getDeck(deckId);

    }

    public boolean deleteDeck(String deckId) {
        Integer userId = ApiUtils.extractUserId();
        DbDeck deck = deckMapper.selectById(deckId);
        if (!isOwnerOrAdmin(deck, userId)) {
            log.warn("Deck delete invalid request for user {}, trying to delete {}", userId, deckId);
            return false;
        }
        deck.setDeleted(true);
        deckMapper.update(deck);
        //Enqueue indexation of new deck
        deckIndex.enqueueRefreshIndex(deck.getId());
        return true;
    }

    public boolean restoreDeck(String deckId) {
        Integer userId = ApiUtils.extractUserId();
        DbDeck deck = deckMapper.selectById(deckId);
        if (deck == null) {
            return false;
        } else if (!isOwnerOrAdmin(deck, userId)) {
            log.warn("Deck restore invalid request for user {}, trying to restore {}", userId, deckId);
            return false;
        }
        deck.setDeleted(false);
        deckMapper.update(deck);
        //Enqueue indexation of new deck
        deckIndex.enqueueRefreshIndex(deck.getId());
        return true;
    }

    private ApiDeckBuilder importDeck(String url, Function<Map<String, ?>, Deck> api) {
        try {
            Map<String, String> form = new HashMap<>();
            form.put("url", url);
            Deck deck = api.apply(form);
            if (deck != null) {
                return importKRCGDeck(deck);
            }
        } catch (FeignException e) {
            log.error("KRCG client error while import {}", url, e);
        }
        return null;
    }

    private ApiDeckBuilder importKRCGDeck(Deck deck) {
        ApiDeckBuilder deckBuilder = new ApiDeckBuilder();
        deckBuilder.setName(deck.getName());
        deckBuilder.setDescription(deck.getComments());
        deckBuilder.setPublished(false);
        deckBuilder.setCards(new ArrayList<>());
        if (deck.getCrypt() != null && deck.getCrypt().getCards() != null) {
            for (Card card : deck.getCrypt().getCards()) {
                deckBuilder.getCards().add(getApiCard(card.getId(), card.getCount()));
            }
        }
        if (deck.getLibrary() != null && deck.getLibrary().getCards() != null) {
            for (Card libraryCard : deck.getLibrary().getCards()) {
                if (libraryCard.getCards() != null) {
                    for (Card card : libraryCard.getCards()) {
                        deckBuilder.getCards().add(getApiCard(card.getId(), card.getCount()));
                    }
                }
            }
        }
        return deckBuilder;
    }

    private ApiCard getApiCard(Integer id, Integer number) {
        ApiCard apiCard = new ApiCard();
        apiCard.setId(id);
        Library library = libraryCache.get(id);
        apiCard.setType(library != null ? library.getType() : null);
        apiCard.setNumber(number);
        return apiCard;
    }

    public boolean updateCollectionTracker(String deckId, Boolean collectionTracker) {
        Integer userId = ApiUtils.extractUserId();
        DbDeck deck = deckMapper.selectById(deckId);
        if (deck == null) {
            return false;
        } else if (!isOwnerOrAdmin(deck, userId) || deck.getType() != DeckType.COMMUNITY) {
            log.warn("Deck update collection tracker invalid request for user {}, tying to update {}", userId, deckId);
            return false;
        }
        deck.setCollection(collectionTracker);
        deckMapper.update(deck);
        //Enqueue indexation of new deck
        deckIndex.enqueueRefreshIndex(deck.getId());
        return true;
    }

    private boolean isOwnerOrAdmin(DbDeck deck, Integer userId) {
        boolean isDeckOwner = deck.getUser() != null && deck.getUser().equals(userId);
        if (isDeckOwner) {
            return true;
        }
        DbUser user = userMapper.selectById(userId);
        return user != null && user.isAdmin();
    }
}
