package com.vtesdecks.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Iterables;
import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.api.mapper.DeckArchetypeMapper;
import com.vtesdecks.api.util.ApiUtils;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.cache.indexable.Library;
import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.cache.indexable.deck.card.Card;
import com.vtesdecks.cache.redis.entity.ArchetypeKeyCard;
import com.vtesdecks.integration.KRCGClient;
import com.vtesdecks.jpa.entity.DeckCardEntity;
import com.vtesdecks.jpa.entity.DeckEntity;
import com.vtesdecks.jpa.entity.UserEntity;
import com.vtesdecks.jpa.repositories.DeckCardRepository;
import com.vtesdecks.jpa.repositories.DeckRepository;
import com.vtesdecks.jpa.repositories.LimitedFormatRepository;
import com.vtesdecks.jpa.repositories.UserRepository;
import com.vtesdecks.messaging.MessageProducer;
import com.vtesdecks.model.DeckQuery;
import com.vtesdecks.model.ImportType;
import com.vtesdecks.model.api.ApiCard;
import com.vtesdecks.model.api.ApiDeckBuilder;
import com.vtesdecks.model.api.ApiDeckBuilderHistory;
import com.vtesdecks.model.api.ApiDeckSuggestedCards;
import com.vtesdecks.service.DeckCardHistoryService;
import com.vtesdecks.service.DeckKeyCardsService;
import com.vtesdecks.service.DeckService;
import com.vtesdecks.util.CosineSimilarityUtils;
import com.vtesdecks.util.VtesUtils;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public static final double MIN_SUGGESTION_SIMILARITY_THRESHOLD = 0.6;

    private final DeckRepository deckRepository;
    private final DeckCardRepository deckCardRepository;
    private final DeckCardHistoryService deckCardHistoryService;
    private final LibraryCache libraryCache;
    private final KRCGClient krcgClient;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final LimitedFormatRepository limitedFormatRepository;
    private final MessageProducer messageProducer;
    private final ApiUserNotificationService apiUserNotificationService;
    private final DeckService deckService;
    private final DeckKeyCardsService deckKeyCardsService;
    private final DeckArchetypeMapper deckArchetypeMapper;


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

    @Transactional
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
        boolean isUpdated = false;
        if (deck == null) {
            deck = new DeckEntity();
            deck.setId(deckId);
            deck.setType(DeckType.COMMUNITY);
            deck.setUser(userId);
            deck.setVerified(true);
            deck.setCreationDate(LocalDateTime.now());
            isUpdated = true;
        } else if (!isOwnerOrAdmin(deck, userId)) {
            log.warn("Deck {} is not valid for user {}", apiDeckBuilder.getId(), userId);
            throw new IllegalArgumentException("Deck " + apiDeckBuilder.getId() + " is not valid for user " + userId);
        }
        deck.setName(apiDeckBuilder.getName());
        deck.setDescription(apiDeckBuilder.getDescription());
        if (apiDeckBuilder.isPublished() && Boolean.FALSE.equals(deck.getPublished())) {
            isUpdated = true;
        }
        deck.setPublished(apiDeckBuilder.isPublished());
        if (deck.getType() == DeckType.COMMUNITY) {
            deck.setExtra(apiDeckBuilder.getExtra());
            deck.setCollection(apiDeckBuilder.isCollection());
        }
        deckRepository.save(deck);
        //Deck cards
        // Capture cursor BEFORE card saves so we can identify trigger rows from this save
        Long preSaveMaxHistoryId = null;
        if (apiDeckBuilder.getTagLabel() != null) {
            Long maxId = deckCardHistoryService.getMaxId(deck.getId());
            preSaveMaxHistoryId = maxId != null ? maxId : 0L;
        }

        List<ApiCard> deckCards = apiDeckBuilder.getCards();
        Iterables.removeIf(deckCards, Objects::isNull);
        List<DeckCardEntity> dbCards = deckCardRepository.findByIdDeckId(deck.getId());
        for (ApiCard card : deckCards) {
            if (card.getNumber() != null) {
                DeckCardEntity dbCard = dbCards.stream().filter(db -> db.getId().getCardId().equals(card.getId())).findFirst().orElse(null);
                if (dbCard == null) {
                    dbCard = new DeckCardEntity();
                    dbCard.setId(new DeckCardEntity.DeckCardId());
                    dbCard.getId().setCardId(card.getId());
                    dbCard.getId().setDeckId(deck.getId());
                    dbCard.setNumber(card.getNumber());
                    deckCardRepository.save(dbCard);
                    isUpdated = true;
                } else if (!dbCard.getNumber().equals(card.getNumber())) {
                    dbCard.setNumber(card.getNumber());
                    deckCardRepository.save(dbCard);
                    isUpdated = true;
                }
            }
        }
        //Delete removed cards
        for (DeckCardEntity card : dbCards) {
            try {
                ApiCard deckCard = deckCards.stream()
                        .filter(dc -> dc.getNumber() != null && dc.getId().equals(card.getId().getCardId()))
                        .findFirst().orElse(null);
                if (deckCard == null) {
                    deckCardRepository.delete(card);
                    isUpdated = true;
                }
            } catch (Exception e) {
                log.error("Unable to delete card {}", card, e);
            }
        }
        //Tag the last trigger-fired history row of this save as a named checkpoint
        if (preSaveMaxHistoryId != null) {
            Integer maxTag = deckCardHistoryService.getMaxTag(deck.getId());
            int nextTag = (maxTag != null ? maxTag : 0) + 1;
            deckCardHistoryService.tagLastEntry(deck.getId(), preSaveMaxHistoryId, nextTag, apiDeckBuilder.getTagLabel());
            log.info("Deck builder user {} saved tag {} ('{}') for deck {}", userId, nextTag, apiDeckBuilder.getTagLabel(), deck.getId());
        }
        //Enqueue indexation of new deck
        messageProducer.publishDeckSync(deck.getId());
        if (Boolean.TRUE.equals(deck.getPublished())) {
            if (isUpdated) {
                apiUserNotificationService.deckUpdateNotifications(deck);
            }
        } else {
            apiUserNotificationService.deckDeleteNotifications(deck.getId());
        }
        return getDeck(deckId);

    }

    public List<ApiDeckBuilderHistory> getDeckBuilderHistory(String deckId) {
        Integer userId = ApiUtils.extractUserId();
        DeckEntity deck = deckRepository.findById(deckId).orElse(null);
        if (deck == null || Boolean.TRUE.equals(deck.getDeleted())) {
            return null;
        }
        if (!isOwnerOrAdmin(deck, userId)) {
            throw new IllegalArgumentException("Deck " + deckId + " is not accessible for user " + userId);
        }

        return deckCardHistoryService.getDeckHistoryAsc(deckId)
                .stream()
                .map(row -> ApiDeckBuilderHistory.builder()
                        .action(row.getAction())
                        .cardId(row.getCardId())
                        .number(row.getNumber())
                        .date(row.getCreationDate())
                        .tag(row.getTag())
                        .tagLabel(row.getTagLabel())
                        .build())
                .toList();
    }

    public boolean deleteDeck(String deckId, boolean permanent) {
        Integer userId = ApiUtils.extractUserId();
        DeckEntity deck = deckRepository.findById(deckId).orElse(null);
        if (!isOwnerOrAdmin(deck, userId)) {
            log.warn("Deck delete invalid request for user {}, trying to delete {}", userId, deckId);
            return false;
        }
        if (permanent) {
            // Remove user reference to be not visible anymore, delete will be done by cleanup scheduler
            deck.setUser(null);
        }
        deck.setDeleted(true);
        deckRepository.save(deck);

        //Enqueue indexation of new deck
        messageProducer.publishDeckSync(deck.getId());
        apiUserNotificationService.deckDeleteNotifications(deck.getId());
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
        if (Boolean.TRUE.equals(deck.getPublished())) {
            apiUserNotificationService.deckUpdateNotifications(deck);
        }
        return true;
    }

    private ApiDeckBuilder importDeck(String url, Function<Map<String, ?>, com.vtesdecks.model.krcg.Deck> api) {
        try {
            Map<String, String> form = new HashMap<>();
            form.put("url", url);
            com.vtesdecks.model.krcg.Deck deck = api.apply(form);
            if (deck != null) {
                return importKRCGDeck(deck);
            }
        } catch (FeignException e) {
            log.error("KRCG client error while import {}", url, e);
        }
        return null;
    }

    private ApiDeckBuilder importKRCGDeck(com.vtesdecks.model.krcg.Deck deck) {
        ApiDeckBuilder deckBuilder = new ApiDeckBuilder();
        deckBuilder.setName(deck.getName());
        deckBuilder.setDescription(deck.getComments());
        deckBuilder.setPublished(false);
        deckBuilder.setCards(new ArrayList<>());
        if (deck.getCrypt() != null && deck.getCrypt().getCards() != null) {
            for (com.vtesdecks.model.krcg.Card card : deck.getCrypt().getCards()) {
                deckBuilder.getCards().add(getApiCard(card.getId(), card.getCount()));
            }
        }
        if (deck.getLibrary() != null && deck.getLibrary().getCards() != null) {
            for (com.vtesdecks.model.krcg.Card libraryCard : deck.getLibrary().getCards()) {
                if (libraryCard.getCards() != null) {
                    for (com.vtesdecks.model.krcg.Card card : libraryCard.getCards()) {
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

    public ApiDeckSuggestedCards getSuggestedCards(List<ApiCard> cards) {
        // Build a transient Deck from the input cards
        Deck inputDeck = buildTemporalDeck(cards);
        Map<Integer, Integer> inputVector = CosineSimilarityUtils.getVector(inputDeck);

        if (inputVector.isEmpty()) {
            return new ApiDeckSuggestedCards();
        }

        // Find all non-deleted decks (TOURNAMENT, COMMUNITY and USER/private) with cosine similarity
        List<Deck> similarDecks = new ArrayList<>();
        try (ResultSet<Deck> deckResultSet = deckService.getDecks(DeckQuery.builder().allDecks(true).build())) {
            for (Deck candidate : deckResultSet) {
                Map<Integer, Integer> candidateVector = CosineSimilarityUtils.getVector(candidate);
                double similarity = CosineSimilarityUtils.cosineSimilarity(inputDeck, inputVector, candidate, candidateVector);
                if (similarity >= MIN_SUGGESTION_SIMILARITY_THRESHOLD) {
                    similarDecks.add(candidate);
                }
            }
        }

        // Compute key cards using a higher threshold (30%) to produce tighter suggestions
        List<ArchetypeKeyCard> keyCards = deckKeyCardsService.computeKeyCards(similarDecks, DeckKeyCardsService.SUGGESTED_CARDS_THRESHOLD);

        // Map to the response model splitting crypt / library
        return ApiDeckSuggestedCards.builder()
                .keyCrypt(deckArchetypeMapper.mapKeyCrypt(keyCards))
                .keyLibrary(deckArchetypeMapper.mapKeyLibrary(keyCards))
                .build();
    }

    private Deck buildTemporalDeck(List<ApiCard> cards) {
        Deck deck = new Deck();
        if (cards != null) {
            for (ApiCard apiCard : cards) {
                if (apiCard.getId() == null || apiCard.getNumber() == null || apiCard.getNumber() <= 0) {
                    continue;
                }
                Card card = Card.builder()
                        .id(apiCard.getId())
                        .number(apiCard.getNumber())
                        .build();
                if (VtesUtils.isCrypt(apiCard.getId())) {
                    deck.getCrypt().add(card);
                } else {
                    deck.getLibraryByType()
                            .computeIfAbsent("library", k -> new ArrayList<>())
                            .add(card);
                }
            }
        }
        Map<Integer, Integer> vector = CosineSimilarityUtils.getVector(deck);
        deck.setL2Norm(CosineSimilarityUtils.computeL2Norm(vector));
        return deck;
    }

    // -------------------------------------------------------------------------

    private boolean isOwnerOrAdmin(DeckEntity deck, Integer userId) {
        boolean isDeckOwner = deck.getUser() != null && deck.getUser().equals(userId);
        if (isDeckOwner) {
            return true;
        }
        UserEntity user = userRepository.findById(userId).orElse(null);
        return user != null && user.getAdmin() != null && user.getAdmin();
    }
}
