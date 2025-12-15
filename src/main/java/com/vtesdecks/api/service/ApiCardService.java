package com.vtesdecks.api.service;

import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.api.mapper.ApiCardMapper;
import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.indexable.Crypt;
import com.vtesdecks.cache.indexable.Library;
import com.vtesdecks.jpa.entity.CardShopEntity;
import com.vtesdecks.jpa.entity.extra.TextSearch;
import com.vtesdecks.jpa.repositories.CardShopRepository;
import com.vtesdecks.jpa.repositories.DeckCardRepository;
import com.vtesdecks.model.api.ApiCrypt;
import com.vtesdecks.model.api.ApiLibrary;
import com.vtesdecks.model.api.ApiShop;
import com.vtesdecks.model.api.ApiShopResult;
import com.vtesdecks.util.VtesUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApiCardService {
    private final CryptCache cryptCache;
    private final LibraryCache libraryCache;
    private final CardShopRepository cardShopRepository;
    private final DeckCardRepository deckCardRepository;
    private final ApiCardMapper apiCardMapper;

    public ApiCrypt getCrypt(Integer id, String locale) {
        Crypt crypt = cryptCache.get(id);
        if (crypt == null) {
            return null;
        }
        return apiCardMapper.mapCrypt(crypt, locale, null, null);
    }

    public List<ApiCrypt> getAllCrypt(String locale) {
        ResultSet<Crypt> result = cryptCache.selectAll(null, null);
        if (result == null) {
            return Collections.emptyList();
        }
        return result.stream()
                .map(card -> apiCardMapper.mapCrypt(card, locale, null, null))
                .collect(Collectors.toList());
    }

    public ApiCrypt getCryptLastUpdate() {
        Crypt crypt = cryptCache.selectLastUpdated();
        return apiCardMapper.mapCrypt(crypt, null, null, null);
    }

    public ApiLibrary getLibrary(Integer id, String locale) {
        Library library = libraryCache.get(id);
        if (library == null) {
            return null;
        }
        return apiCardMapper.mapLibrary(library, locale, null, null);
    }

    public List<ApiLibrary> getAllLibrary(String locale) {
        ResultSet<Library> result = libraryCache.selectAll(null, null);
        if (result == null) {
            return Collections.emptyList();
        }
        return result.stream()
                .map(card -> apiCardMapper.mapLibrary(card, locale, null, null))
                .toList();
    }

    public ApiLibrary getLibraryLastUpdate() {
        Library library = libraryCache.selectLastUpdated();
        return apiCardMapper.mapLibrary(library, null, null, null);
    }

    public ApiShopResult getCardShops(Integer cardId, boolean showAll) {
        List<CardShopEntity> all = cardShopRepository.findByCardId(cardId)
                .stream()
                .filter(cardShop -> cardShop.getPlatform().isEnabled())
                .sorted(Comparator.comparing(CardShopEntity::getPrice))
                .toList();
        if (showAll) {
            return ApiShopResult.builder().shops(apiCardMapper.mapCardShop(all)).hasMore(false).build();
        }
        List<ApiShop> groupedByShop = apiCardMapper.mapCardShop(all.stream()
                .filter(cardShop -> cardShop.getPlatform().isEnabled())
                .collect(Collectors.groupingBy(CardShopEntity::getPlatform))
                .values()
                .stream()
                .map(shops -> shops.stream()
                        .filter(shop -> shop.getSet() == null)
                        .findFirst()
                        .orElseGet(() -> shops.stream()
                                .min(Comparator.comparing(CardShopEntity::getPrice))
                                .orElseGet(shops::getFirst))
                )
                .sorted(Comparator.comparing(CardShopEntity::getPrice))
                .toList());

        return ApiShopResult.builder().shops(groupedByShop).hasMore(all.size() > groupedByShop.size()).build();
    }

    public List<Object> searchCards(String query, Integer limit, Set<String> fields) {
        try (ResultSet<Crypt> crypt = cryptCache.selectByExactName(query)) {
            if (crypt.isNotEmpty()) {
                return crypt.stream()
                        .map(card -> apiCardMapper.mapCrypt(card, null, fields, 100.0))
                        .limit(limit != null ? limit : crypt.size())
                        .collect(Collectors.toList());
            }
        }
        try (ResultSet<Library> library = libraryCache.selectByExactName(query)) {
            if (library.isNotEmpty()) {
                return library.stream()
                        .map(card -> apiCardMapper.mapLibrary(card, null, fields, 100.0))
                        .limit(limit != null ? limit : library.size())
                        .collect(Collectors.toList());
            }
        }
        List<TextSearch> results = deckCardRepository.search(query);
        // Return list with ApiLibrary and ApiCrypt objects
        return results.stream()
                .map(textSearch -> {
                    if (VtesUtils.isCrypt(textSearch.getId())) {
                        Crypt crypt = cryptCache.get(textSearch.getId());
                        return apiCardMapper.mapCrypt(crypt, null, fields, textSearch.getScore());
                    } else {
                        Library library = libraryCache.get(textSearch.getId());
                        return apiCardMapper.mapLibrary(library, null, fields, textSearch.getScore());
                    }
                })
                .limit(limit != null ? limit : results.size())
                .toList();
    }
}
