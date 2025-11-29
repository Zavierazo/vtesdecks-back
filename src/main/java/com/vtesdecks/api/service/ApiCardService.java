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
import com.vtesdecks.util.VtesUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ApiCardService {
    @Autowired
    private CryptCache cryptCache;
    @Autowired
    private LibraryCache libraryCache;
    @Autowired
    private CardShopRepository cardShopRepository;
    @Autowired
    private DeckCardRepository deckCardRepository;
    @Autowired
    private ApiCardMapper apiCardMapper;

    public ApiCrypt getCrypt(Integer id, String locale) {
        Crypt crypt = cryptCache.get(id);
        if (crypt == null) {
            return null;
        }
        return apiCardMapper.mapCrypt(crypt, locale, null);
    }

    public List<ApiCrypt> getAllCrypt(String locale) {
        ResultSet<Crypt> result = cryptCache.selectAll(null, null);
        if (result == null) {
            return Collections.emptyList();
        }
        return result.stream()
                .map(card -> apiCardMapper.mapCrypt(card, locale, null))
                .collect(Collectors.toList());
    }

    public ApiCrypt getCryptLastUpdate() {
        Crypt crypt = cryptCache.selectLastUpdated();
        return apiCardMapper.mapCrypt(crypt, null, null);
    }

    public ApiLibrary getLibrary(Integer id, String locale) {
        Library library = libraryCache.get(id);
        if (library == null) {
            return null;
        }
        return apiCardMapper.mapLibrary(library, locale, null);
    }

    public List<ApiLibrary> getAllLibrary(String locale) {
        ResultSet<Library> result = libraryCache.selectAll(null, null);
        if (result == null) {
            return Collections.emptyList();
        }
        return result.stream()
                .map(card -> apiCardMapper.mapLibrary(card, locale, null))
                .toList();
    }

    public ApiLibrary getLibraryLastUpdate() {
        Library library = libraryCache.selectLastUpdated();
        return apiCardMapper.mapLibrary(library, null, null);
    }

    public List<ApiShop> getCardShops(Integer cardId, Boolean showAll) {
        List<CardShopEntity> cardShopList = cardShopRepository.findByCardId(cardId);
        if (Boolean.TRUE.equals(showAll)) {
            return apiCardMapper.mapCardShop(cardShopList);
        }
        return apiCardMapper.mapCardShop(cardShopList.stream()
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
    }

    public List<Object> searchCards(String query) {
        List<TextSearch> results = deckCardRepository.search(query);
        // Return list with ApiLibrary and ApiCrypt objects
        return results.stream()
                .map(textSearch -> {
                    if (VtesUtils.isCrypt(textSearch.getId())) {
                        Crypt crypt = cryptCache.get(textSearch.getId());
                        return apiCardMapper.mapCrypt(crypt, null, textSearch.getScore());
                    } else {
                        Library library = libraryCache.get(textSearch.getId());
                        return apiCardMapper.mapLibrary(library, null, textSearch.getScore());
                    }
                })
                .toList();
    }
}
