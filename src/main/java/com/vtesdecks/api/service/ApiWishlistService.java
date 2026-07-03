package com.vtesdecks.api.service;

import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.api.mapper.ApiWishlistMapper;
import com.vtesdecks.api.util.ApiUtils;
import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.indexable.Crypt;
import com.vtesdecks.cache.indexable.Library;
import com.vtesdecks.jpa.entity.UserEntity;
import com.vtesdecks.jpa.entity.WishlistCardEntity;
import com.vtesdecks.jpa.repositories.UserRepository;
import com.vtesdecks.jpa.repositories.WishlistCardRepository;
import com.vtesdecks.jpa.repositories.WishlistCardRepositoryCustom;
import com.vtesdecks.model.api.ApiWishlistCard;
import com.vtesdecks.model.api.ApiWishlistPage;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApiWishlistService {
    private static final List<String> ALLOWED_FILTERS = List.of("cardId", "cardType", "set", "cardName", "priority");

    private final UserRepository userRepository;
    private final WishlistCardRepository wishlistCardRepository;
    private final WishlistCardRepositoryCustom wishlistCardRepositoryCustom;
    private final ApiWishlistMapper apiWishlistMapper;
    private final CryptCache cryptCache;
    private final LibraryCache libraryCache;

    public ApiWishlistPage<ApiWishlistCard> getWishlist(Integer page, Integer size, String sortBy, String sortDirection, Map<String, String> params, String currencyCode) throws Exception {
        try {
            Integer userId = ApiUtils.extractUserId();
            ApiWishlistPage<ApiWishlistCard> result = queryWishlist(userId, page, size, sortBy, sortDirection, params, currencyCode);
            result.setPublicVisibility(isWishlistPublic(userId));
            return result;
        } catch (IllegalArgumentException e) {
            throw e; // Propagate validation errors
        } catch (Exception e) {
            throw new Exception("An unexpected error occurred while retrieving the wishlist", e);
        }
    }

    public boolean updateVisibility(boolean publicVisibility) throws Exception {
        try {
            Integer userId = ApiUtils.extractUserId();
            UserEntity user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User does not exist"));
            user.setWishlistPublicVisibility(publicVisibility);
            userRepository.save(user);
            return publicVisibility;
        } catch (IllegalArgumentException e) {
            throw e; // Propagate validation errors
        } catch (Exception e) {
            throw new Exception("An unexpected error occurred while updating the wishlist visibility", e);
        }
    }

    public ApiWishlistCard addCard(ApiWishlistCard card, String currencyCode) throws Exception {
        try {
            Integer userId = ApiUtils.extractUserId();
            if (card.getCardId() == null) {
                throw new IllegalArgumentException("Card id cannot be empty");
            }
            if (card.getNumber() == null || card.getNumber() <= 0) {
                card.setNumber(1);
            }
            List<WishlistCardEntity> existingCards = wishlistCardRepository.findByUserIdAndCardIdAndSetAndConditionAndLanguage(
                    userId, card.getCardId(), card.getSet(),
                    card.getCondition() != null ? card.getCondition().name() : null, card.getLanguage());
            if (!CollectionUtils.isEmpty(existingCards)) {
                WishlistCardEntity existingCard = existingCards.getFirst();
                existingCard.setNumber(existingCard.getNumber() + card.getNumber());
                if (card.getPriority() != null) {
                    existingCard.setPriority(card.getPriority());
                }
                if (card.getNotes() != null) {
                    existingCard.setNotes(card.getNotes());
                }
                return apiWishlistMapper.mapWishlistCard(wishlistCardRepository.save(existingCard), currencyCode);
            }
            WishlistCardEntity entity = apiWishlistMapper.mapWishlistCardToEntity(card);
            entity.setUserId(userId);
            return apiWishlistMapper.mapWishlistCard(wishlistCardRepository.save(entity), currencyCode);
        } catch (IllegalArgumentException e) {
            throw e; // Propagate validation errors
        } catch (Exception e) {
            throw new Exception("An unexpected error occurred while adding the card to the wishlist", e);
        }
    }

    public List<ApiWishlistCard> addCardsBulk(List<ApiWishlistCard> cards, String currencyCode) throws Exception {
        List<ApiWishlistCard> result = new ArrayList<>();
        for (ApiWishlistCard card : cards) {
            result.add(addCard(card, currencyCode));
        }
        return result;
    }

    public ApiWishlistCard updateCard(Integer id, ApiWishlistCard card, String currencyCode) throws Exception {
        try {
            Integer userId = ApiUtils.extractUserId();
            WishlistCardEntity existingCard = wishlistCardRepository.findByUserIdAndId(userId, id)
                    .orElseThrow(() -> new IllegalArgumentException("Card does not exist"));
            existingCard.setNumber(card.getNumber() != null && card.getNumber() > 0 ? card.getNumber() : 1);
            existingCard.setPriority(card.getPriority());
            existingCard.setSet(card.getSet());
            existingCard.setCondition(card.getCondition() != null ? card.getCondition().name() : null);
            existingCard.setLanguage(card.getLanguage());
            existingCard.setNotes(card.getNotes());
            return apiWishlistMapper.mapWishlistCard(wishlistCardRepository.save(existingCard), currencyCode);
        } catch (IllegalArgumentException e) {
            throw e; // Propagate validation errors
        } catch (Exception e) {
            throw new Exception("An unexpected error occurred while updating the wishlist card", e);
        }
    }

    public boolean deleteCard(Integer id) throws Exception {
        try {
            Integer userId = ApiUtils.extractUserId();
            WishlistCardEntity existingCard = wishlistCardRepository.findByUserIdAndId(userId, id)
                    .orElseThrow(() -> new IllegalArgumentException("Card does not exist"));
            wishlistCardRepository.delete(existingCard);
            return true;
        } catch (IllegalArgumentException e) {
            throw e; // Propagate validation errors
        } catch (Exception e) {
            throw new Exception("An unexpected error occurred while deleting the wishlist card", e);
        }
    }

    public ApiWishlistPage<ApiWishlistCard> getUserPublicWishlist(String username, Integer page, Integer size, String sortBy, String sortDirection, Map<String, String> params, String currencyCode) throws Exception {
        try {
            UserEntity user = userRepository.findByUsername(username);
            if (user == null || !Boolean.TRUE.equals(user.getWishlistPublicVisibility())) {
                return null;
            }
            ApiWishlistPage<ApiWishlistCard> result = queryWishlist(user.getId(), page, size, sortBy, sortDirection, params, currencyCode);
            result.setPublicVisibility(true);
            return result;
        } catch (IllegalArgumentException e) {
            throw e; // Propagate validation errors
        } catch (Exception e) {
            throw new Exception("An unexpected error occurred while retrieving the public wishlist", e);
        }
    }

    private ApiWishlistPage<ApiWishlistCard> queryWishlist(Integer userId, Integer page, Integer size, String sortBy, String sortDirection, Map<String, String> params, String currencyCode) {
        Sort.Direction sortDirectionEnum = StringUtils.equalsIgnoreCase(sortDirection, "desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        String sortByEntity = StringUtils.isNotBlank(sortBy) ? sortBy : "cardName";
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirectionEnum, sortByEntity));
        Map<String, String> filters = buildFilters(params);
        return apiWishlistMapper.mapWishlistPage(wishlistCardRepositoryCustom.findByDynamicFilters(userId, filters, pageable), currencyCode);
    }

    private Map<String, String> buildFilters(Map<String, String> params) {
        Map<String, String> filters = params != null ? params.entrySet().stream()
                .filter(entry -> ALLOWED_FILTERS.contains(entry.getKey()))
                .filter(entry -> StringUtils.isNotEmpty(entry.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)) : new HashMap<>();
        if (params != null && (params.containsKey("cardTypes") || params.containsKey("cardClans") || params.containsKey("cardDisciplines"))) {
            Set<Integer> filteredIds = getCardIdFilter(params.get("cardTypes"), params.get("cardClans"), params.get("cardDisciplines"));
            if (filters.containsKey("cardId")) {
                filteredIds.retainAll(Arrays.stream(filters.get("cardId").split(","))
                        .map(Integer::parseInt)
                        .collect(Collectors.toSet()));
            }
            filters.put("cardId", filteredIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(",")));
        }
        return filters;
    }

    private Set<Integer> getCardIdFilter(String cardTypes, String cardClans, String cardDisciplines) {
        Set<Integer> filteredIds = new HashSet<>();
        List<String> cardTypeList = StringUtils.isEmpty(cardTypes) ? null : Arrays.stream(cardTypes.split(",")).toList();
        List<String> cardClanList = StringUtils.isEmpty(cardClans) ? null : Arrays.stream(cardClans.split(",")).toList();
        List<String> cardDisciplineList = StringUtils.isEmpty(cardDisciplines) ? null : Arrays.stream(cardDisciplines.split(",")).toList();
        try (ResultSet<Crypt> result = cryptCache.selectAll(cardTypeList, cardClanList, cardDisciplineList)) {
            result.stream().forEach(crypt -> filteredIds.add(crypt.getId()));
        }
        try (ResultSet<Library> result = libraryCache.selectAll(cardTypeList, cardClanList, cardDisciplineList)) {
            result.stream().forEach(library -> filteredIds.add(library.getId()));
        }
        return filteredIds;
    }

    public Integer getWishlistNumber(Integer userId, Integer cardId) {
        if (userId == null || cardId == null) {
            return null;
        }
        return wishlistCardRepository.sumNumberByUserIdAndCardId(userId, cardId);
    }

    private boolean isWishlistPublic(Integer userId) {
        // Defaults to public (true) when the user or flag is somehow absent
        return userRepository.findById(userId)
                .map(UserEntity::getWishlistPublicVisibility)
                .map(Boolean.TRUE::equals)
                .orElse(true);
    }
}
