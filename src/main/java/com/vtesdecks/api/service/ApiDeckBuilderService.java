package com.vtesdecks.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Iterables;
import com.vtesdecks.api.util.ApiUtils;
import com.vtesdecks.cache.DeckIndex;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.indexable.Library;
import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.integration.KRCGClient;
import com.vtesdecks.jpa.entity.DeckCardEntity;
import com.vtesdecks.jpa.entity.DeckEntity;
import com.vtesdecks.jpa.entity.UserEntity;
import com.vtesdecks.jpa.repositories.DeckCardRepository;
import com.vtesdecks.jpa.repositories.DeckRepository;
import com.vtesdecks.jpa.repositories.LimitedFormatRepository;
import com.vtesdecks.jpa.repositories.UserRepository;
import com.vtesdecks.messaging.MessageProducer;
import com.vtesdecks.model.ImportType;
import com.vtesdecks.model.api.ApiCard;
import com.vtesdecks.model.api.ApiDeckBuilder;
import com.vtesdecks.model.krcg.Card;
import com.vtesdecks.model.krcg.Deck;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
@RequiredArgsConstructor
public class ApiDeckBuilderService {
    private final DeckRepository deckRepository;
    private final DeckCardRepository deckCardRepository;
    private final LibraryCache libraryCache;
    private final KRCGClient krcgClient;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final DeckIndex deckIndex;
    private final LimitedFormatRepository limitedFormatRepository;
    private final MessageProducer messageProducer;


    public ApiDeckBuilder getDeck(String deckId) {
        Integer userId = ApiUtils.extractUserId();
        DeckEntity deck = deckRepository.findById(deckId).orElse(null);
        if (deck == null || !isOwnerOrAdmin(deck, userId) || Boolean.TRUE.equals(deck.getDeleted())) {
            return null;
        }
        ApiDeckBuilder deckBuilder = new ApiDeckBuilder();
        deckBuilder.setId(deck.getId());
        deckBuilder.setName(deck.getName());
        deckBuilder.setDescription(deck.getDescription());
        deckBuilder.setPublished(deck.getPublished());
        deckBuilder.setCollection(deck.getCollection());
        deckBuilder.setCards(new ArrayList<>());
        deckBuilder.setExtra(deck.getExtra());
        if (deckBuilder.getExtra() != null && deckBuilder.getExtra().has("limitedFormat")) {
            updatePredefinedLimitedFormat(deckBuilder.getExtra());
        }
        List<DeckCardEntity> dbDeckCards = deckCardRepository.findByIdDeckId(deck.getId());
        for (DeckCardEntity deckCard : dbDeckCards) {
            ApiCard apiCard = getApiCard(deckCard.getId().getCardId(), deckCard.getNumber());
            deckBuilder.getCards().add(apiCard);
        }
        return deckBuilder;
    }

    private void updatePredefinedLimitedFormat(JsonNode extra) {
        if (extra.get("limitedFormat").has("id")) {
            Integer formatId = extra.get("limitedFormat").get("id").asInt();
            limitedFormatRepository.findById(formatId).ifPresent(format -> {
                try {
                    JsonNode formatNode = objectMapper.valueToTree(format.getFormat());
                    ((ObjectNode) extra).set("limitedFormat", formatNode);
                } catch (Exception e) {
                    log.error("Unable to set predefined limited format {}", formatId, e);
                }
            });
        }
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
        DeckEntity deck = null;
        if (apiDeckBuilder.getId() != null) {
            deck = deckRepository.findById(apiDeckBuilder.getId()).orElse(null);
        }
        String deckId = apiDeckBuilder.getId();
        if (deckId == null) {
            UserEntity user = userRepository.findById(userId).orElse(null);
            deckId = "user-" + StringUtils.lowerCase(user.getUsername()) + "-" + UUID.randomUUID().toString().replace("-", "");
        }
        if (deck == null) {
            deck = new DeckEntity();
            deck.setId(deckId);
            deck.setType(DeckType.COMMUNITY);
            deck.setUser(userId);
            deck.setVerified(true);
            deck.setCreationDate(LocalDateTime.now());
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
        deckRepository.save(deck);
        //Deck cards
        List<ApiCard> deckCards = apiDeckBuilder.getCards();
        Iterables.removeIf(deckCards, Objects::isNull);
        List<DeckCardEntity> dbCards = deckCardRepository.findByIdDeckId(deck.getId());
        for (ApiCard card : deckCards) {
            if (card.getNumber() != null && card.getNumber() > 0) {
                DeckCardEntity dbCard = dbCards.stream().filter(db -> db.getId().getCardId().equals(card.getId())).findFirst().orElse(null);
                if (dbCard == null) {
                    dbCard = new DeckCardEntity();
                    dbCard.setId(new DeckCardEntity.DeckCardId());
                    dbCard.getId().setCardId(card.getId());
                    dbCard.getId().setDeckId(deck.getId());
                    dbCard.setNumber(card.getNumber());
                    deckCardRepository.save(dbCard);
                } else if (!dbCard.getNumber().equals(card.getNumber())) {
                    dbCard.setNumber(card.getNumber());
                    deckCardRepository.save(dbCard);
                }
            }
        }
        //Delete removed cards
        for (DeckCardEntity card : dbCards) {
            try {
                Integer deckCard = deckCards.stream().map(ApiCard::getId).filter(id -> id.equals(card.getId().getCardId())).findFirst().orElse(null);
                if (deckCard == null) {
                    deckCardRepository.delete(card);
                }
            } catch (Exception e) {
                log.error("Unable to delete card {}", card, e);
            }
        }
        //Enqueue indexation of new deck
        messageProducer.publishDeckSync(deck.getId());
        return getDeck(deckId);

    }

    public boolean deleteDeck(String deckId) {
        Integer userId = ApiUtils.extractUserId();
        DeckEntity deck = deckRepository.findById(deckId).orElse(null);
        if (!isOwnerOrAdmin(deck, userId)) {
            log.warn("Deck delete invalid request for user {}, trying to delete {}", userId, deckId);
            return false;
        }
        deck.setDeleted(true);
        deckRepository.save(deck);
        //Enqueue indexation of new deck
        messageProducer.publishDeckSync(deck.getId());
        return true;
    }

    public boolean restoreDeck(String deckId) {
        Integer userId = ApiUtils.extractUserId();
        DeckEntity deck = deckRepository.findById(deckId).orElse(null);
        if (deck == null) {
            return false;
        } else if (!isOwnerOrAdmin(deck, userId)) {
            log.warn("Deck restore invalid request for user {}, trying to restore {}", userId, deckId);
            return false;
        }
        deck.setDeleted(false);
        deckRepository.save(deck);
        //Enqueue indexation of new deck
        messageProducer.publishDeckSync(deck.getId());
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
        DeckEntity deck = deckRepository.findById(deckId).orElse(null);
        if (deck == null) {
            return false;
        } else if (!isOwnerOrAdmin(deck, userId) || deck.getType() != DeckType.COMMUNITY) {
            log.warn("Deck update collection tracker invalid request for user {}, tying to update {}", userId, deckId);
            return false;
        }
        deck.setCollection(collectionTracker);
        deckRepository.save(deck);
        //Enqueue indexation of new deck
        messageProducer.publishDeckSync(deck.getId());
        return true;
    }

    private boolean isOwnerOrAdmin(DeckEntity deck, Integer userId) {
        boolean isDeckOwner = deck.getUser() != null && deck.getUser().equals(userId);
        if (isDeckOwner) {
            return true;
        }
        UserEntity user = userRepository.findById(userId).orElse(null);
        return user != null && user.getAdmin() != null && user.getAdmin();
    }
}
