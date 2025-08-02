package com.vtesdecks.api.service;

import com.vtesdecks.api.mapper.ApiCollectionMapper;
import com.vtesdecks.api.util.ApiUtils;
import com.vtesdecks.jpa.entities.Collection;
import com.vtesdecks.jpa.entities.CollectionBinder;
import com.vtesdecks.jpa.entities.CollectionCard;
import com.vtesdecks.jpa.repositories.CollectionBinderRepository;
import com.vtesdecks.jpa.repositories.CollectionCardRepository;
import com.vtesdecks.jpa.repositories.CollectionCardRepositoryCustom;
import com.vtesdecks.jpa.repositories.CollectionRepository;
import com.vtesdecks.model.CollectionType;
import com.vtesdecks.model.DeckSort;
import com.vtesdecks.model.DeckType;
import com.vtesdecks.model.api.ApiCollection;
import com.vtesdecks.model.api.ApiCollectionBinder;
import com.vtesdecks.model.api.ApiCollectionCard;
import com.vtesdecks.model.api.ApiCollectionCardCsv;
import com.vtesdecks.model.api.ApiCollectionCardStats;
import com.vtesdecks.model.api.ApiCollectionImport;
import com.vtesdecks.model.api.ApiCollectionPage;
import com.vtesdecks.model.api.ApiDecks;
import com.vtesdecks.util.Utils;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.vtesdecks.util.Constants.CARDS_DELETED_HEADER;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;

@Service
@RequiredArgsConstructor
public class ApiCollectionService {
    private final CollectionRepository collectionRepository;
    private final CollectionBinderRepository collectionBinderRepository;
    private final CollectionCardRepository collectionCardRepository;
    private final CollectionCardRepositoryCustom collectionCardRepositoryCustom;
    private final ApiCollectionMapper apiCollectionMapper;
    private final ApiCollectionImportService apiCollectionImportService;
    private final ApiDeckService apiDeckService;

    public ApiCollection getCollection() throws Exception {
        try {
            Collection collection = getCollectionOrCreate();
            return apiCollectionMapper.mapCollection(collection, collectionBinderRepository.findByCollectionId(collection.getId()));
        } catch (IllegalArgumentException e) {
            throw e; // Propagate validation errors
        } catch (Exception e) {
            throw new Exception("An unexpected error occurred while retrieving the collection", e);
        }
    }

    public ApiCollection resetCollection() throws Exception {
        try {
            Collection collection = getCollectionOrCreate();
            collection.setDeleted(true);
            collectionRepository.save(collection);
            Collection newCollection = getCollectionOrCreate();
            return apiCollectionMapper.mapCollection(newCollection, collectionBinderRepository.findByCollectionId(newCollection.getId()));
        } catch (IllegalArgumentException e) {
            throw e; // Propagate validation errors
        } catch (Exception e) {
            throw new Exception("An unexpected error occurred while resetting the collection", e);
        }
    }

    public List<ApiCollectionBinder> getBinders() throws Exception {
        try {
            Collection collection = getCollectionOrCreate();
            return apiCollectionMapper.mapBinders(collectionBinderRepository.findByCollectionId(collection.getId()));
        } catch (IllegalArgumentException e) {
            throw e; // Propagate validation errors
        } catch (Exception e) {
            throw new Exception("An unexpected error occurred while getting the binders", e);
        }
    }


    public ApiCollectionBinder getBinder(Integer id) throws Exception {
        try {
            Collection collection = getCollectionOrCreate();
            return collectionBinderRepository.findByCollectionIdAndId(collection.getId(), id)
                    .map(apiCollectionMapper::mapBinder)
                    .orElseThrow(() -> new IllegalArgumentException("Binder does not exist"));
        } catch (IllegalArgumentException e) {
            throw e; // Propagate validation errors
        } catch (Exception e) {
            throw new Exception("An unexpected error occurred while getting the binders", e);
        }
    }

    public ApiCollectionBinder getPublicBinder(String publicHash) throws Exception {
        try {
            CollectionBinder binder = collectionBinderRepository.findByPublicHash(publicHash)
                    .orElseThrow(() -> new IllegalArgumentException("Binder does not exist"));
            return apiCollectionMapper.mapBinder(binder);
        } catch (IllegalArgumentException e) {
            throw e; // Propagate validation errors
        } catch (Exception e) {
            throw new Exception("An unexpected error occurred while retrieving the public binder", e);
        }
    }

    public ApiCollectionBinder createBinder(ApiCollectionBinder binder) throws Exception {
        try {
            Collection collection = getCollectionOrCreate();
            if (StringUtils.isBlank(binder.getName())) {
                throw new IllegalArgumentException("Binder name cannot be empty");
            } else if (collectionBinderRepository.existsByCollectionIdAndNameIgnoreCase(collection.getId(), binder.getName())) {
                throw new IllegalArgumentException("Binder with this name already exists in the collection");
            }
            CollectionBinder collectionBinder = apiCollectionMapper.mapBinderEntity(binder);
            collectionBinder.setCollectionId(collection.getId());
            if (collectionBinder.isPublicVisibility()) {
                String publicHash;
                do {
                    publicHash = ApiUtils.generatePublicHash();
                } while (collectionBinderRepository.existsByPublicHash(publicHash));
                collectionBinder.setPublicHash(publicHash);
            } else {
                collectionBinder.setPublicHash(null);
            }
            return apiCollectionMapper.mapBinder(collectionBinderRepository.save(collectionBinder));
        } catch (IllegalArgumentException e) {
            throw e; // Propagate validation errors
        } catch (Exception e) {
            throw new Exception("An unexpected error occurred while creating the binder", e);
        }
    }

    public ApiCollectionBinder updateBinder(Integer id, ApiCollectionBinder binder) throws Exception {
        try {
            Collection collection = getCollectionOrCreate();
            CollectionBinder existingBinder = collectionBinderRepository.findByCollectionIdAndId(collection.getId(), id)
                    .orElseThrow(() -> new IllegalArgumentException("Binder does not exist"));
            if (StringUtils.isBlank(binder.getName())) {
                throw new IllegalArgumentException("Binder name cannot be empty");
            } else if (collectionBinderRepository.existsByCollectionIdAndNameIgnoreCase(collection.getId(), binder.getName())
                    && !existingBinder.getName().equalsIgnoreCase(binder.getName())) {
                throw new IllegalArgumentException("Binder with this name already exists in the collection");
            }
            existingBinder.setName(binder.getName());
            existingBinder.setIcon(binder.getIcon());
            existingBinder.setPublicVisibility(binder.isPublicVisibility());
            existingBinder.setDescription(binder.getDescription());
            if (existingBinder.isPublicVisibility() && StringUtils.isBlank(existingBinder.getPublicHash())) {
                String publicHash;
                do {
                    publicHash = ApiUtils.generatePublicHash();
                } while (collectionBinderRepository.existsByPublicHash(publicHash));
                existingBinder.setPublicHash(publicHash);
            } else if (!existingBinder.isPublicVisibility()) {
                existingBinder.setPublicHash(null);
            }
            return apiCollectionMapper.mapBinder(collectionBinderRepository.save(existingBinder));
        } catch (IllegalArgumentException e) {
            throw e; // Propagate validation errors
        } catch (Exception e) {
            throw new Exception("An unexpected error occurred while updating the binder", e);
        }
    }

    @Transactional
    public Boolean deleteBinder(Integer id, boolean deleteCards) throws Exception {
        try {
            Collection collection = getCollectionOrCreate();
            CollectionBinder binder = collectionBinderRepository.findByCollectionIdAndId(collection.getId(), id)
                    .orElseThrow(() -> new IllegalArgumentException("Binder does not exist"));
            if (collectionCardRepository.existsByBinderId(id)) {
                if (deleteCards) {
                    collectionCardRepository.deleteByBinderId(id);
                } else {
                    collectionCardRepository.clearBinderId(id);
                }
            }
            collectionBinderRepository.delete(binder);
            return true;
        } catch (IllegalArgumentException e) {
            throw e; // Propagate validation errors
        } catch (Exception e) {
            throw new Exception("An unexpected error occurred while deleting the binder", e);
        }
    }


    private Collection getCollectionOrCreate() {
        Integer userId = ApiUtils.extractUserId();
        List<Collection> collectionList = collectionRepository.findByUserIdAndDeletedFalse(userId);
        if (collectionList.isEmpty()) {
            // If no collection exists, return new Collection
            Collection newCollection = new Collection();
            newCollection.setUserId(userId);
            newCollection.setDeleted(false);
            return collectionRepository.save(newCollection);
        } else if (collectionList.size() > 1) {
            // If multiple collections exist, log an error or handle it as needed
            throw new IllegalStateException("Multiple collections found for user ID: " + userId);
        }
        return collectionList.getFirst();
    }

    public ApiCollectionPage<ApiCollectionCard> getPublicCards(String publicHash, Integer page, Integer size, String groupBy, String sortBy, String sortDirection, Map<String, String> filters) throws Exception {
        try {
            CollectionBinder binder = collectionBinderRepository.findByPublicHash(publicHash).orElseThrow(() -> new IllegalArgumentException("Binder does not exist"));
            Sort.Direction sortDirectionEnum = StringUtils.equalsIgnoreCase(sortDirection, "desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
            String sortByEntity = StringUtils.isNotBlank(sortBy) ? sortBy : "cardName";
            Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirectionEnum, sortByEntity));
            filters.put("binderId", String.valueOf(binder.getId()));
            if (groupBy != null) {
                return apiCollectionMapper.mapCards(collectionCardRepositoryCustom.findByDynamicFiltersGroupBy(binder.getCollectionId(), filters, groupBy, pageable));
            } else {
                return apiCollectionMapper.mapCards(collectionCardRepositoryCustom.findByDynamicFilters(binder.getCollectionId(), filters, pageable));
            }
        } catch (IllegalArgumentException e) {
            throw e; // Propagate validation errors
        } catch (Exception e) {
            throw new Exception("An unexpected error occurred while retrieving the cards", e);
        }
    }

    public ApiCollectionPage<ApiCollectionCard> getCards(Integer page, Integer size, String groupBy, String sortBy, String sortDirection, Map<String, String> filters) throws Exception {
        try {
            Collection collection = getCollectionOrCreate();
            Sort.Direction sortDirectionEnum = StringUtils.equalsIgnoreCase(sortDirection, "desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
            String sortByEntity = StringUtils.isNotBlank(sortBy) ? sortBy : "cardName";
            Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirectionEnum, sortByEntity));
            if (groupBy != null) {
                return apiCollectionMapper.mapCards(collectionCardRepositoryCustom.findByDynamicFiltersGroupBy(collection.getId(), filters, groupBy, pageable));
            } else {
                return apiCollectionMapper.mapCards(collectionCardRepositoryCustom.findByDynamicFilters(collection.getId(), filters, pageable));
            }

        } catch (IllegalArgumentException e) {
            throw e; // Propagate validation errors
        } catch (Exception e) {
            throw new Exception("An unexpected error occurred while retrieving the cards", e);
        }
    }

    public List<ApiCollectionCard> getCardsById(List<Integer> ids) {
        try {
            Collection collection = getCollectionOrCreate();
            List<CollectionCard> cards = collectionCardRepository.findByCollectionIdAndCardIdIn(collection.getId(), ids);
            return apiCollectionMapper.mapCards(cards);
        } catch (IllegalArgumentException e) {
            throw e; // Propagate validation errors
        } catch (Exception e) {
            throw new RuntimeException("An unexpected error occurred while retrieving the cards by ID", e);
        }
    }

    public ApiCollectionCard createCards(ApiCollectionCard card, HttpServletResponse response) throws Exception {
        try {
            Collection collection = getCollectionOrCreate();
            if (card.getBinderId() != null && !collectionBinderRepository.existsByCollectionIdAndId(collection.getId(), card.getBinderId())) {
                throw new IllegalArgumentException("Binder does not exist in the collection");
            }
            CollectionCard collectionCard = apiCollectionMapper.mapCardToEntity(card);
            collectionCard.setCollectionId(collection.getId());
            List<CollectionCard> existingCars = collectionCardRepository.findByCollectionIdAndCardIdAndSetAndConditionAndLanguageAndBinderId(
                    collection.getId(), card.getCardId(), card.getSet(), card.getCondition() != null ? card.getCondition().name() : null, card.getLanguage(), card.getBinderId());
            if (!CollectionUtils.isEmpty(existingCars)) {
                // If a card with the same attributes already exists, return it instead of creating a new one
                CollectionCard existingCard = existingCars.getFirst();
                existingCard.setNumber(existingCard.getNumber() + card.getNumber());
                if (existingCars.size() > 1) {
                    List<Integer> deleteIds = new ArrayList<>();
                    for (CollectionCard duplicateCard : existingCars.subList(1, existingCars.size())) {
                        existingCard.setNumber(existingCard.getNumber() + card.getNumber());
                        deleteIds.add(duplicateCard.getId());
                        collectionCardRepository.delete(duplicateCard);
                    }
                    if (!deleteIds.isEmpty()) {
                        response.addHeader(CARDS_DELETED_HEADER, StringUtils.join(deleteIds, ","));
                    }
                }
                return apiCollectionMapper.mapCard(collectionCardRepository.save(existingCard));
            } else {
                return apiCollectionMapper.mapCard(collectionCardRepository.save(collectionCard));
            }
        } catch (IllegalArgumentException e) {
            throw e; // Propagate validation errors
        } catch (Exception e) {
            throw new Exception("An unexpected error occurred while creating the card", e);
        }
    }

    public ApiCollectionCard updateCard(Integer id, ApiCollectionCard card, HttpServletResponse response) throws Exception {
        try {
            Collection collection = getCollectionOrCreate();
            CollectionCard existingCard = collectionCardRepository.findByCollectionIdAndId(collection.getId(), id)
                    .orElseThrow(() -> new IllegalArgumentException("Card does not exist"));
            if (card.getBinderId() != null && !collectionBinderRepository.existsByCollectionIdAndId(collection.getId(), card.getBinderId())) {
                throw new IllegalArgumentException("Binder does not exist in the collection");
            }
            existingCard.setNumber(card.getNumber());
            existingCard.setSet(card.getSet());
            existingCard.setCondition(card.getCondition() != null ? card.getCondition().name() : null);
            existingCard.setLanguage(card.getLanguage());
            existingCard.setBinderId(card.getBinderId());
            existingCard.setNotes(card.getNotes());
            List<CollectionCard> existingCards = collectionCardRepository.findByCollectionIdAndCardIdAndSetAndConditionAndLanguageAndBinderId(
                    collection.getId(), card.getCardId(), card.getSet(), card.getCondition() != null ? card.getCondition().name() : null, card.getLanguage(), card.getBinderId());
            if (!CollectionUtils.isEmpty(existingCards)) {
                List<Integer> deleteIds = new ArrayList<>();
                for (CollectionCard duplicateCard : existingCards) {
                    if (!duplicateCard.getId().equals(existingCard.getId())) {
                        // If a card with the same attributes already exists, update the number and delete the duplicate
                        existingCard.setNumber(existingCard.getNumber() + duplicateCard.getNumber());
                        deleteIds.add(duplicateCard.getId());
                        collectionCardRepository.delete(duplicateCard);
                    }
                }
                if (!deleteIds.isEmpty()) {
                    response.addHeader(CARDS_DELETED_HEADER, StringUtils.join(deleteIds, ","));
                }
            }
            return apiCollectionMapper.mapCard(collectionCardRepository.save(existingCard));
        } catch (IllegalArgumentException e) {
            throw e; // Propagate validation errors
        } catch (Exception e) {
            throw new Exception("An unexpected error occurred while updating the card", e);
        }
    }

    public Boolean deleteCard(List<Integer> ids) throws Exception {
        try {
            Collection collection = getCollectionOrCreate();
            List<CollectionCard> card = collectionCardRepository.findByCollectionIdAndIdIn(collection.getId(), ids);
            if (card.isEmpty()) {
                throw new IllegalArgumentException("No cards found with the provided IDs");
            }
            collectionCardRepository.deleteAllById(card.stream().map(CollectionCard::getId).toList());
            return true;
        } catch (IllegalArgumentException e) {
            throw e; // Propagate validation errors
        } catch (Exception e) {
            throw new Exception("An unexpected error occurred while deleting the card", e);
        }
    }


    public ApiCollectionCard moveCardToBinder(Integer id, Integer binderId, Integer quantity) throws Exception {
        try {
            Collection collection = getCollectionOrCreate();
            CollectionCard card = collectionCardRepository.findByCollectionIdAndId(collection.getId(), id)
                    .orElseThrow(() -> new IllegalArgumentException("Card does not exist"));
            if (binderId != null && !collectionBinderRepository.existsByCollectionIdAndId(collection.getId(), binderId)) {
                throw new IllegalArgumentException("Binder does not exist in the collection");
            }
            if (quantity <= 0) {
                throw new IllegalArgumentException("Quantity must be greater than zero");
            }
            if (card.getNumber() < quantity) {
                throw new IllegalArgumentException("Not enough cards to move");
            }
            if (card.getBinderId() != null && card.getBinderId().equals(binderId)) {
                throw new IllegalArgumentException("Card is already in the specified binder");
            }
            if (binderId == null && card.getBinderId() == null) {
                throw new IllegalArgumentException("Card is already not in any binder");
            }
            card.setNumber(card.getNumber() - quantity);
            if (card.getNumber() <= 0) {
                collectionCardRepository.delete(card);
            } else {
                collectionCardRepository.save(card);
            }
            List<CollectionCard> existingCard = collectionCardRepository.findByCollectionIdAndCardIdAndSetAndConditionAndLanguageAndBinderId(
                    collection.getId(), card.getCardId(), card.getSet(), card.getCondition(), card.getLanguage(), binderId);
            if (!CollectionUtils.isEmpty(existingCard)) {
                CollectionCard existing = existingCard.getFirst();
                existing.setNumber(existing.getNumber() + quantity);
                return apiCollectionMapper.mapCard(collectionCardRepository.save(existing));
            } else {
                CollectionCard newCard = new CollectionCard();
                newCard.setCollectionId(collection.getId());
                newCard.setCardId(card.getCardId());
                newCard.setSet(card.getSet());
                newCard.setNumber(quantity);
                newCard.setBinderId(binderId);
                newCard.setCondition(card.getCondition());
                newCard.setLanguage(card.getLanguage());
                newCard.setNotes(card.getNotes());
                return apiCollectionMapper.mapCard(collectionCardRepository.save(newCard));
            }
        } catch (IllegalArgumentException e) {
            throw e; // Propagate validation errors
        } catch (Exception e) {
            throw new Exception("An unexpected error occurred while moving the card to the binder", e);
        }
    }


    public List<ApiCollectionCard> bulkEditCards(List<Integer> ids, Integer binderId, String condition, String language, HttpServletResponse response) throws Exception {
        try {
            Collection collection = getCollectionOrCreate();
            List<CollectionCard> cards = collectionCardRepository.findByCollectionIdAndIdIn(collection.getId(), ids);
            if (cards.isEmpty()) {
                throw new IllegalArgumentException("No cards found with the provided IDs");
            }
            // Apply modifications to the cards
            for (CollectionCard card : cards) {
                if (binderId != null) {
                    if (binderId == 0) {
                        card.setBinderId(null);
                    } else if (!collectionBinderRepository.existsByCollectionIdAndId(collection.getId(), binderId)) {
                        throw new IllegalArgumentException("Binder does not exist in the collection");
                    } else {
                        card.setBinderId(binderId);
                    }
                }
                if (StringUtils.isNotBlank(condition)) {
                    if ("none".equalsIgnoreCase(condition)) {
                        card.setCondition(null);
                    } else {
                        card.setCondition(condition);
                    }
                }
                if (StringUtils.isNotBlank(language)) {
                    card.setLanguage(language);
                }
            }
            collectionCardRepository.saveAll(cards);
            // Delete duplicated cards
            List<Integer> deleteIds = new ArrayList<>();
            for (CollectionCard card : cards) {
                List<CollectionCard> existingCards = collectionCardRepository.findByCollectionIdAndCardIdAndSetAndConditionAndLanguageAndBinderId(
                        collection.getId(), card.getCardId(), card.getSet(), card.getCondition(), card.getLanguage(), card.getBinderId());
                for (CollectionCard duplicateCard : existingCards) {
                    if (cards.stream().noneMatch(c -> duplicateCard.getId().equals(c.getId()))) {
                        // If a card with the same attributes already exists, update the number and delete the duplicate
                        card.setNumber(card.getNumber() + duplicateCard.getNumber());
                        deleteIds.add(duplicateCard.getId());
                    }
                }
            }
            collectionCardRepository.saveAll(cards);
            // Delete duplicated cards in bulk execution
            List<CollectionCard> cardWithoutDuplicates = new ArrayList<>();
            for (CollectionCard card : cards) {
                Optional<CollectionCard> existingCard = cardWithoutDuplicates.stream()
                        .filter(c -> Objects.equals(c.getCardId(), card.getCardId())
                                && Objects.equals(c.getSet(), card.getSet())
                                && Objects.equals(c.getCondition(), card.getCondition())
                                && Objects.equals(c.getLanguage(), card.getLanguage())
                                && Objects.equals(c.getBinderId(), card.getBinderId()))
                        .findFirst();
                if (existingCard.isPresent()) {
                    existingCard.get().setNumber(existingCard.get().getNumber() + card.getNumber());
                    deleteIds.add(card.getId());
                } else {
                    cardWithoutDuplicates.add(card);
                }
            }
            collectionCardRepository.saveAll(cardWithoutDuplicates);
            if (!deleteIds.isEmpty()) {
                response.addHeader(CARDS_DELETED_HEADER, StringUtils.join(deleteIds, ","));
                collectionCardRepository.deleteAllById(deleteIds);
            }
            return apiCollectionMapper.mapCards(cardWithoutDuplicates);
        } catch (IllegalArgumentException e) {
            throw e; // Propagate validation errors
        } catch (Exception e) {
            throw new Exception("An unexpected error occurred while bulk edit the cards", e);
        }
    }

    public void export(HttpServletResponse response, Integer binderId) throws Exception {
        Collection collection = getCollectionOrCreate();
        List<CollectionBinder> binders = collectionBinderRepository.findByCollectionId(collection.getId());
        List<CollectionCard> cards = binderId != null ? collectionCardRepository.findByCollectionIdAndBinderId(collection.getId(), binderId) : collectionCardRepository.findByCollectionId(collection.getId());
        List<ApiCollectionCardCsv> apiCards = apiCollectionMapper.mapCsv(cards, binders);
        String nowDate = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .replace(":", "_")
                .replace("T", "_")
                .replace("-", "_")
                .substring(0, 16);
        String fileName = "vtesdecks_collection_" + nowDate + ".csv";
        Utils.returnCsv(response, fileName, ApiCollectionCardCsv.FIELDS_ORDER, apiCards, ApiCollectionCardCsv.class);
    }

    public ApiCollectionImport importCards(CollectionType type, MultipartFile file, Integer binderId) {
        Collection collection = getCollectionOrCreate();
        switch (type) {
            case VTESDECKS:
                return apiCollectionImportService.importCardsVtesDecks(collection, file, binderId);
            case LACKEY:
                return apiCollectionImportService.importCardsLackey(collection, file, binderId);
            case VDB:
                return apiCollectionImportService.importCardsVDB(collection, file, binderId);
            default:
                throw new IllegalArgumentException("Unsupported collection type: " + type);
        }
    }

    public ApiCollectionCardStats getCardStats(Integer id, Boolean mini) throws Exception {
        Collection collection = getCollectionOrCreate();
        try {
            List<CollectionCard> cards = collectionCardRepository.findByCollectionIdAndCardId(collection.getId(), id);
            ApiDecks decks = apiDeckService.getDecks(DeckType.USER, DeckSort.NEWEST, ApiUtils.extractUserId(), null, null, null,
                    null, null, List.of(id + "=1"), null, null, null, null,
                    null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null,
                    null, null, null, null, null, 0, 10);
            ApiCollectionCardStats stats = new ApiCollectionCardStats();
            stats.setCollectionNumber(cards.stream().map(CollectionCard::getNumber).filter(Objects::nonNull).mapToInt(Integer::intValue).sum());
            stats.setDecksNumber(decks.getDecks().stream()
                    .mapToInt(deck -> !isEmpty(deck.getFilterCards()) ? deck.getFilterCards().getFirst().getNumber() : 0)
                    .sum());
            stats.setTrackedDecksNumber(decks.getDecks().stream()
                    .filter(deck -> Boolean.TRUE.equals(deck.getCollection()))
                    .mapToInt(deck -> !isEmpty(deck.getFilterCards()) ? deck.getFilterCards().getFirst().getNumber() : 0)
                    .sum());
            if (!Boolean.TRUE.equals(mini)) {
                stats.setCollectionCards(apiCollectionMapper.mapCards(cards));
                stats.setDecks(decks);
            }
            return stats;
        } catch (IllegalArgumentException e) {
            throw e; // Propagate validation errors
        } catch (Exception e) {
            throw new Exception("An unexpected error occurred while obtaining card stats", e);
        }
    }


}
