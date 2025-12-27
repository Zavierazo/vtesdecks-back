package com.vtesdecks.api.service;

import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.api.mapper.ApiCardMapper;
import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.indexable.Card;
import com.vtesdecks.cache.indexable.Crypt;
import com.vtesdecks.cache.indexable.Library;
import com.vtesdecks.jpa.entity.CardShopEntity;
import com.vtesdecks.jpa.repositories.CardShopRepository;
import com.vtesdecks.model.api.ApiCrypt;
import com.vtesdecks.model.api.ApiLibrary;
import com.vtesdecks.model.api.ApiShop;
import com.vtesdecks.model.api.ApiShopResult;
import com.vtesdecks.util.TrigramSimilarity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ApiCardService {
    private static final BigDecimal MIN_TRIGRAMS_SCORE = BigDecimal.valueOf(0.25);

    private final CryptCache cryptCache;
    private final LibraryCache libraryCache;
    private final CardShopRepository cardShopRepository;
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

    public List<Object> searchCards(String query, Double minScore, Integer limit, Set<String> fields) {
        BigDecimal targetScore = minScore != null ? BigDecimal.valueOf(minScore) : MIN_TRIGRAMS_SCORE;
        try (ResultSet<Crypt> cryptResult = cryptCache.selectAll(null, null); ResultSet<Library> libraryResult = libraryCache.selectAll(null, null);) {
            Set<String> queryTrigrams = TrigramSimilarity.generateTrigram(query);
            return Stream.concat(cryptResult.stream(), libraryResult.stream())
                    .map((Card card) -> {
                        BigDecimal trigramsScore = TrigramSimilarity.trigramSimilarity(card.getName(), query, card.getNameTrigrams(), queryTrigrams);
                        if (trigramsScore.compareTo(targetScore) < 0) {
                            trigramsScore = TrigramSimilarity.trigramSimilarity(card.getAka(), query, card.getAkaTrigrams(), queryTrigrams);
                        }
                        if (trigramsScore.compareTo(targetScore) > 0) {
                            if (card instanceof Crypt crypt) {
                                return apiCardMapper.mapCrypt(crypt, null, fields, trigramsScore.doubleValue());
                            } else if (card instanceof Library library) {
                                return apiCardMapper.mapLibrary(library, null, fields, trigramsScore.doubleValue());
                            }
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparingDouble(card -> {
                        if (card instanceof ApiCrypt crypt) {
                            return crypt.getScore();
                        } else {
                            return ((ApiLibrary) card).getScore();
                        }
                    }).reversed())
                    .limit(limit != null ? limit : Long.MAX_VALUE)
                    .toList();
        }
    }
}
