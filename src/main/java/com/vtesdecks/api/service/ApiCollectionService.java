package com.vtesdecks.api.service;

import com.vtesdecks.api.mapper.ApiCollectionMapper;
import com.vtesdecks.api.util.ApiUtils;
import com.vtesdecks.jpa.entity.CollectionBinderEntity;
import com.vtesdecks.jpa.entity.CollectionCardEntity;
import com.vtesdecks.jpa.entity.CollectionEntity;
import com.vtesdecks.jpa.entity.UserEntity;
import com.vtesdecks.jpa.repositories.CollectionBinderRepository;
import com.vtesdecks.jpa.repositories.CollectionCardRepository;
import com.vtesdecks.jpa.repositories.CollectionCardRepositoryCustom;
import com.vtesdecks.jpa.repositories.CollectionRepository;
import com.vtesdecks.jpa.repositories.UserRepository;
import com.vtesdecks.model.CollectionType;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.vtesdecks.util.Constants.CARDS_DELETED_HEADER;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;

@Service
@RequiredArgsConstructor
public class ApiCollectionService {
    private final UserRepository userRepository;
    private final CollectionRepository collectionRepository;
    private final CollectionBinderRepository collectionBinderRepository;
    private final CollectionCardRepository collectionCardRepository;
    private final CollectionCardRepositoryCustom collectionCardRepositoryCustom;
    private final ApiCollectionMapper apiCollectionMapper;
    private final ApiCollectionImportService apiCollectionImportService;

    public ApiCollection getCollection() throws Exception {
        try {
            CollectionEntity collectionEntity = getCollectionOrCreate();
            return apiCollectionMapper.mapCollection(collectionEntity, collectionBinderRepository.findByCollectionId(collectionEntity.getId()));
        } catch (IllegalArgumentException e) {
            throw e; // Propagate validation errors
        } catch (Exception e) {
            throw new Exception("An unexpected error occurred while retrieving the collection", e);
        }
    }

    public ApiCollection resetCollection() throws Exception {
        try {
            CollectionEntity collectionEntity = getCollectionOrCreate();
            collectionEntity.setDeleted(true);
            collectionRepository.save(collectionEntity);
            CollectionEntity newCollectionEntity = getCollectionOrCreate();
            return apiCollectionMapper.mapCollection(newCollectionEntity, collectionBinderRepository.findByCollectionId(newCollectionEntity.getId()));
        } catch (IllegalArgumentException e) {
            throw e; // Propagate validation errors
        } catch (Exception e) {
            throw new Exception("An unexpected error occurred while resetting the collection", e);
        }
    }

    public List<ApiCollectionBinder> getBinders() throws Exception {
        try {
            CollectionEntity collectionEntity = getCollectionOrCreate();
            return apiCollectionMapper.mapBinders(collectionBinderRepository.findByCollectionId(collectionEntity.getId()));
        } catch (IllegalArgumentException e) {
            throw e; // Propagate validation errors
        } catch (Exception e) {
            throw new Exception("An unexpected error occurred while getting the binders", e);
        }
    }


    public ApiCollectionBinder getBinder(Integer id) throws Exception {
        try {
            CollectionEntity collectionEntity = getCollectionOrCreate();
            return collectionBinderRepository.findByCollectionIdAndId(collectionEntity.getId(), id)
                    .map(apiCollectionMapper::mapBinder)
                    .orElseThrow(() -> new IllegalArgumentException("Binder does not exist"));
        } catch (IllegalArgumentException e) {
            throw e; // Propagate validation errors
        } catch (Exception e) {
            throw new Exception("An unexpected error occurred while getting the binders", e);
        }
    }

    public ApiCollection getUserPublicCollection(String username) throws Exception {
        try {
            UserEntity user = userRepository.findByUsername(username);
            if (user == null) {
                return null;
            }
            CollectionEntity collectionEntity = getCollection(user.getId());
            List<CollectionBinderEntity> binders = collectionEntity != null
                    ? collectionBinderRepository.findByCollectionIdAndPublicVisibilityTrue(collectionEntity.getId())
                    : null;
            return apiCollectionMapper.mapCollection(collectionEntity, binders);
        } catch (IllegalArgumentException e) {
            throw e; // Propagate validation errors
        } catch (Exception e) {
            throw new Exception("An unexpected error occurred while retrieving the public collection", e);
        }
    }

    public ApiCollectionBinder getPublicBinder(String publicHash) throws Exception {
        try {
            CollectionBinderEntity binder = collectionBinderRepository.findByPublicHash(publicHash)
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
            CollectionEntity collectionEntity = getCollectionOrCreate();
            if (StringUtils.isBlank(binder.getName())) {
                throw new IllegalArgumentException("Binder name cannot be empty");
            } else if (collectionBinderRepository.existsByCollectionIdAndNameIgnoreCase(collectionEntity.getId(), binder.getName())) {
                throw new IllegalArgumentException("Binder with this name already exists in the collection");
            }
            CollectionBinderEntity collectionBinderEntity = apiCollectionMapper.mapBinderEntity(binder);
            collectionBinderEntity.setCollectionId(collectionEntity.getId());
            if (collectionBinderEntity.isPublicVisibility()) {
                String publicHash;
                do {
                    publicHash = ApiUtils.generatePublicHash();
                } while (collectionBinderRepository.existsByPublicHash(publicHash));
                collectionBinderEntity.setPublicHash(publicHash);
            } else {
                collectionBinderEntity.setPublicHash(null);
            }
            return apiCollectionMapper.mapBinder(collectionBinderRepository.save(collectionBinderEntity));
        } catch (IllegalArgumentException e) {
            throw e; // Propagate validation errors
        } catch (Exception e) {
            throw new Exception("An unexpected error occurred while creating the binder", e);
        }
    }

    public ApiCollectionBinder updateBinder(Integer id, ApiCollectionBinder binder) throws Exception {
        try {
            CollectionEntity collectionEntity = getCollectionOrCreate();
            CollectionBinderEntity existingBinder = collectionBinderRepository.findByCollectionIdAndId(collectionEntity.getId(), id)
                    .orElseThrow(() -> new IllegalArgumentException("Binder does not exist"));
            if (StringUtils.isBlank(binder.getName())) {
                throw new IllegalArgumentException("Binder name cannot be empty");
            } else if (collectionBinderRepository.existsByCollectionIdAndNameIgnoreCase(collectionEntity.getId(), binder.getName())
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
            CollectionEntity collectionEntity = getCollectionOrCreate();
            CollectionBinderEntity binder = collectionBinderRepository.findByCollectionIdAndId(collectionEntity.getId(), id)
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

    private CollectionEntity getCollection(Integer userId) {
        List<CollectionEntity> collectionEntityList = collectionRepository.findByUserIdAndDeletedFalse(userId);
        if (collectionEntityList.isEmpty()) {
            return null;
        }
        return collectionEntityList.getFirst();
    }


    private CollectionEntity getCollectionOrCreate() {
        Integer userId = ApiUtils.extractUserId();
        List<CollectionEntity> collectionEntityList = collectionRepository.findByUserIdAndDeletedFalse(userId);
        if (collectionEntityList.isEmpty()) {
            return createCollection(userId);
        } else if (collectionEntityList.size() > 1) {
            // If multiple collections exist, log an error or handle it as needed
            throw new IllegalStateException("Multiple collections found for user ID: " + userId);
        }
        return collectionEntityList.getFirst();
    }

    private synchronized CollectionEntity createCollection(Integer userId) {
        List<CollectionEntity> collectionEntityList = collectionRepository.findByUserIdAndDeletedFalse(userId);
        if (collectionEntityList.isEmpty()) {
            // If no collection exists, return new Collection
            CollectionEntity newCollectionEntity = new CollectionEntity();
            newCollectionEntity.setUserId(userId);
            newCollectionEntity.setDeleted(false);
            return collectionRepository.save(newCollectionEntity);
        }
        return collectionEntityList.getFirst();

    }

    public ApiCollectionPage<ApiCollectionCard> getPublicCards(String publicHash, Integer page, Integer size, String groupBy, String sortBy, String sortDirection, Map<String, String> filters, String currencyCode) throws Exception {
        try {
            CollectionBinderEntity binder = collectionBinderRepository.findByPublicHash(publicHash).orElseThrow(() -> new IllegalArgumentException("Binder does not exist"));
            Sort.Direction sortDirectionEnum = StringUtils.equalsIgnoreCase(sortDirection, "desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
            String sortByEntity = StringUtils.isNotBlank(sortBy) ? sortBy : "cardName";
            Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirectionEnum, sortByEntity));
            filters.put("binderId", String.valueOf(binder.getId()));
            if (groupBy != null) {
                return apiCollectionMapper.mapCards(collectionCardRepositoryCustom.findByDynamicFiltersGroupBy(binder.getCollectionId(), filters, groupBy, pageable), currencyCode);
            } else {
                return apiCollectionMapper.mapCards(collectionCardRepositoryCustom.findByDynamicFilters(binder.getCollectionId(), filters, pageable), currencyCode);
            }
        } catch (IllegalArgumentException e) {
            throw e; // Propagate validation errors
        } catch (Exception e) {
            throw new Exception("An unexpected error occurred while retrieving the cards", e);
        }
    }

    public ApiCollectionPage<ApiCollectionCard> getCards(Integer page, Integer size, String groupBy, String sortBy, String sortDirection, Map<String, String> filters, String currencyCode) throws Exception {
        try {
            CollectionEntity collectionEntity = getCollectionOrCreate();
            Sort.Direction sortDirectionEnum = StringUtils.equalsIgnoreCase(sortDirection, "desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
            String sortByEntity = StringUtils.isNotBlank(sortBy) ? sortBy : "cardName";
            Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirectionEnum, sortByEntity));
            if (groupBy != null) {
                return apiCollectionMapper.mapCards(collectionCardRepositoryCustom.findByDynamicFiltersGroupBy(collectionEntity.getId(), filters, groupBy, pageable), currencyCode);
            } else {
                return apiCollectionMapper.mapCards(collectionCardRepositoryCustom.findByDynamicFilters(collectionEntity.getId(), filters, pageable), currencyCode);
            }

        } catch (IllegalArgumentException e) {
            throw e; // Propagate validation errors
        } catch (Exception e) {
            throw new Exception("An unexpected error occurred while retrieving the cards", e);
        }
    }

    public List<ApiCollectionCard> getCardsById(List<Integer> ids, String currencyCode) {
        try {
            CollectionEntity collectionEntity = getCollectionOrCreate();
            List<CollectionCardEntity> cards = collectionCardRepository.findByCollectionIdAndCardIdIn(collectionEntity.getId(), ids);
            return apiCollectionMapper.mapCards(cards, currencyCode);
        } catch (IllegalArgumentException e) {
            throw e; // Propagate validation errors
        } catch (Exception e) {
            throw new RuntimeException("An unexpected error occurred while retrieving the cards by ID", e);
        }
    }

    public ApiCollectionCard createCards(ApiCollectionCard card, HttpServletResponse response, String currencyCode) throws Exception {
        try {
            CollectionEntity collectionEntity = getCollectionOrCreate();
            if (card.getBinderId() != null && !collectionBinderRepository.existsByCollectionIdAndId(collectionEntity.getId(), card.getBinderId())) {
                throw new IllegalArgumentException("Binder does not exist in the collection");
            }
            CollectionCardEntity collectionCardEntity = apiCollectionMapper.mapCardToEntity(card);
            collectionCardEntity.setCollectionId(collectionEntity.getId());
            List<CollectionCardEntity> existingCars = collectionCardRepository.findByCollectionIdAndCardIdAndSetAndConditionAndLanguageAndBinderId(
                    collectionEntity.getId(), card.getCardId(), card.getSet(), card.getCondition() != null ? card.getCondition().name() : null, card.getLanguage(), card.getBinderId());
            if (!CollectionUtils.isEmpty(existingCars)) {
                // If a card with the same attributes already exists, return it instead of creating a new one
                CollectionCardEntity existingCard = existingCars.getFirst();
                existingCard.setNumber(existingCard.getNumber() + card.getNumber());
                if (existingCars.size() > 1) {
                    List<Integer> deleteIds = new ArrayList<>();
                    for (CollectionCardEntity duplicateCard : existingCars.subList(1, existingCars.size())) {
                        existingCard.setNumber(existingCard.getNumber() + card.getNumber());
                        deleteIds.add(duplicateCard.getId());
                        collectionCardRepository.delete(duplicateCard);
                    }
                    if (!deleteIds.isEmpty()) {
                        response.addHeader(CARDS_DELETED_HEADER, StringUtils.join(deleteIds, ","));
                    }
                }
                return apiCollectionMapper.mapCard(collectionCardRepository.save(existingCard), currencyCode);
            } else {
                return apiCollectionMapper.mapCard(collectionCardRepository.save(collectionCardEntity), currencyCode);
            }
        } catch (IllegalArgumentException e) {
            throw e; // Propagate validation errors
        } catch (Exception e) {
            throw new Exception("An unexpected error occurred while creating the card", e);
        }
    }

    public List<ApiCollectionCard> createCardsBulk(List<ApiCollectionCard> cards, HttpServletResponse response, String currencyCode) throws Exception {
        List<ApiCollectionCard> result = new ArrayList<>();
        for (ApiCollectionCard card : cards) {
            result.add(createCards(card, response, currencyCode));
        }
        return result;
    }

    public ApiCollectionCard updateCard(Integer id, ApiCollectionCard card, HttpServletResponse response, String currencyCode) throws Exception {
        try {
            CollectionEntity collectionEntity = getCollectionOrCreate();
            CollectionCardEntity existingCard = collectionCardRepository.findByCollectionIdAndId(collectionEntity.getId(), id)
                    .orElseThrow(() -> new IllegalArgumentException("Card does not exist"));
            if (card.getBinderId() != null && !collectionBinderRepository.existsByCollectionIdAndId(collectionEntity.getId(), card.getBinderId())) {
                throw new IllegalArgumentException("Binder does not exist in the collection");
            }
            existingCard.setNumber(card.getNumber());
            existingCard.setSet(card.getSet());
            existingCard.setCondition(card.getCondition() != null ? card.getCondition().name() : null);
            existingCard.setLanguage(card.getLanguage());
            existingCard.setBinderId(card.getBinderId());
            existingCard.setNotes(card.getNotes());
            List<CollectionCardEntity> existingCards = collectionCardRepository.findByCollectionIdAndCardIdAndSetAndConditionAndLanguageAndBinderId(
                    collectionEntity.getId(), card.getCardId(), card.getSet(), card.getCondition() != null ? card.getCondition().name() : null, card.getLanguage(), card.getBinderId());
            if (!CollectionUtils.isEmpty(existingCards)) {
                List<Integer> deleteIds = new ArrayList<>();
                for (CollectionCardEntity duplicateCard : existingCards) {
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
            return apiCollectionMapper.mapCard(collectionCardRepository.save(existingCard), currencyCode);
        } catch (IllegalArgumentException e) {
            throw e; // Propagate validation errors
        } catch (Exception e) {
            throw new Exception("An unexpected error occurred while updating the card", e);
        }
    }

    public Boolean deleteCard(List<Integer> ids) throws Exception {
        try {
            CollectionEntity collectionEntity = getCollectionOrCreate();
            List<CollectionCardEntity> card = collectionCardRepository.findByCollectionIdAndIdIn(collectionEntity.getId(), ids);
            if (card.isEmpty()) {
                throw new IllegalArgumentException("No cards found with the provided IDs");
            }
            collectionCardRepository.deleteAllById(card.stream().map(CollectionCardEntity::getId).toList());
            return true;
        } catch (IllegalArgumentException e) {
            throw e; // Propagate validation errors
        } catch (Exception e) {
            throw new Exception("An unexpected error occurred while deleting the card", e);
        }
    }


    public ApiCollectionCard moveCardToBinder(Integer id, Integer binderId, Integer quantity, String currencyCode) throws Exception {
        try {
            CollectionEntity collectionEntity = getCollectionOrCreate();
            CollectionCardEntity card = collectionCardRepository.findByCollectionIdAndId(collectionEntity.getId(), id)
                    .orElseThrow(() -> new IllegalArgumentException("Card does not exist"));
            if (binderId != null && !collectionBinderRepository.existsByCollectionIdAndId(collectionEntity.getId(), binderId)) {
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
            List<CollectionCardEntity> existingCard = collectionCardRepository.findByCollectionIdAndCardIdAndSetAndConditionAndLanguageAndBinderId(
                    collectionEntity.getId(), card.getCardId(), card.getSet(), card.getCondition(), card.getLanguage(), binderId);
            if (!CollectionUtils.isEmpty(existingCard)) {
                CollectionCardEntity existing = existingCard.getFirst();
                existing.setNumber(existing.getNumber() + quantity);
                return apiCollectionMapper.mapCard(collectionCardRepository.save(existing), currencyCode);
            } else {
                CollectionCardEntity newCard = new CollectionCardEntity();
                newCard.setCollectionId(collectionEntity.getId());
                newCard.setCardId(card.getCardId());
                newCard.setSet(card.getSet());
                newCard.setNumber(quantity);
                newCard.setBinderId(binderId);
                newCard.setCondition(card.getCondition());
                newCard.setLanguage(card.getLanguage());
                newCard.setNotes(card.getNotes());
                return apiCollectionMapper.mapCard(collectionCardRepository.save(newCard), currencyCode);
            }
        } catch (IllegalArgumentException e) {
            throw e; // Propagate validation errors
        } catch (Exception e) {
            throw new Exception("An unexpected error occurred while moving the card to the binder", e);
        }
    }


    public List<ApiCollectionCard> bulkEditCards(List<Integer> ids, Integer binderId, String condition, String language, HttpServletResponse response, String currencyCode) throws Exception {
        try {
            CollectionEntity collectionEntity = getCollectionOrCreate();
            List<CollectionCardEntity> cards = collectionCardRepository.findByCollectionIdAndIdIn(collectionEntity.getId(), ids);
            if (cards.isEmpty()) {
                throw new IllegalArgumentException("No cards found with the provided IDs");
            }
            // Apply modifications to the cards
            for (CollectionCardEntity card : cards) {
                if (binderId != null) {
                    if (binderId == 0) {
                        card.setBinderId(null);
                    } else if (!collectionBinderRepository.existsByCollectionIdAndId(collectionEntity.getId(), binderId)) {
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
            for (CollectionCardEntity card : cards) {
                List<CollectionCardEntity> existingCards = collectionCardRepository.findByCollectionIdAndCardIdAndSetAndConditionAndLanguageAndBinderId(
                        collectionEntity.getId(), card.getCardId(), card.getSet(), card.getCondition(), card.getLanguage(), card.getBinderId());
                for (CollectionCardEntity duplicateCard : existingCards) {
                    if (cards.stream().noneMatch(c -> duplicateCard.getId().equals(c.getId()))) {
                        // If a card with the same attributes already exists, update the number and delete the duplicate
                        card.setNumber(card.getNumber() + duplicateCard.getNumber());
                        deleteIds.add(duplicateCard.getId());
                    }
                }
            }
            collectionCardRepository.saveAll(cards);
            // Delete duplicated cards in bulk execution
            List<CollectionCardEntity> cardWithoutDuplicates = new ArrayList<>();
            for (CollectionCardEntity card : cards) {
                Optional<CollectionCardEntity> existingCard = cardWithoutDuplicates.stream()
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
            return apiCollectionMapper.mapCards(cardWithoutDuplicates, currencyCode);
        } catch (IllegalArgumentException e) {
            throw e; // Propagate validation errors
        } catch (Exception e) {
            throw new Exception("An unexpected error occurred while bulk edit the cards", e);
        }
    }

    public void export(HttpServletResponse response, Integer binderId) throws Exception {
        CollectionEntity collectionEntity = getCollectionOrCreate();
        List<CollectionBinderEntity> binders = collectionBinderRepository.findByCollectionId(collectionEntity.getId());
        List<CollectionCardEntity> cards = binderId != null ? collectionCardRepository.findByCollectionIdAndBinderId(collectionEntity.getId(), binderId) : collectionCardRepository.findByCollectionId(collectionEntity.getId());
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
        CollectionEntity collectionEntity = getCollectionOrCreate();
        switch (type) {
            case VTESDECKS:
                return apiCollectionImportService.importCardsVtesDecks(collectionEntity, file, binderId);
            case LACKEY:
                return apiCollectionImportService.importCardsLackey(collectionEntity, file, binderId);
            case VDB:
                return apiCollectionImportService.importCardsVDB(collectionEntity, file, binderId);
            default:
                throw new IllegalArgumentException("Unsupported collection type: " + type);
        }
    }

    public ApiCollectionCardStats getCardStats(Integer id, ApiDecks decks, Boolean summary) throws Exception {
        CollectionEntity collectionEntity = getCollectionOrCreate();
        try {
            List<CollectionCardEntity> cards = collectionCardRepository.findByCollectionIdAndCardId(collectionEntity.getId(), id);
            ApiCollectionCardStats stats = new ApiCollectionCardStats();
            stats.setCollectionNumber(cards.stream().map(CollectionCardEntity::getNumber).filter(Objects::nonNull).mapToInt(Integer::intValue).sum());
            stats.setDecksNumber(decks.getDecks().stream()
                    .mapToInt(deck -> !isEmpty(deck.getFilterCards()) ? deck.getFilterCards().getFirst().getNumber() : 0)
                    .sum());
            stats.setTrackedDecksNumber(decks.getDecks().stream()
                    .filter(deck -> Boolean.TRUE.equals(deck.getCollection()))
                    .mapToInt(deck -> !isEmpty(deck.getFilterCards()) ? deck.getFilterCards().getFirst().getNumber() : 0)
                    .sum());
            if (!Boolean.TRUE.equals(summary)) {
                stats.setCollectionCards(apiCollectionMapper.mapCards(cards, null));
                stats.setDecks(decks);
            }
            return stats;
        } catch (IllegalArgumentException e) {
            throw e; // Propagate validation errors
        } catch (Exception e) {
            throw new Exception("An unexpected error occurred while obtaining card stats", e);
        }
    }

    public Map<Integer, Integer> getCollectionCardsMap() {
        Integer userId = ApiUtils.extractUserId();
        List<CollectionEntity> collectionEntity = collectionRepository.findByUserIdAndDeletedFalse(userId);
        if (collectionEntity != null && !collectionEntity.isEmpty()) {
            List<CollectionCardEntity> cards = collectionCardRepository.findByCollectionId(collectionEntity.getFirst().getId());
            return cards.stream().collect(Collectors.toMap(CollectionCardEntity::getCardId, CollectionCardEntity::getNumber, Integer::sum));
        } else {
            return new HashMap<>();
        }
    }


}
