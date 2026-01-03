package com.vtesdecks.api.service;

import com.vtesdecks.api.mapper.ApiCardErrataMapper;
import com.vtesdecks.api.util.ApiUtils;
import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.jpa.repositories.CardErrataRepository;
import com.vtesdecks.model.DeckSort;
import com.vtesdecks.model.DeckType;
import com.vtesdecks.model.api.ApiCardErrata;
import com.vtesdecks.model.api.ApiCardInfo;
import com.vtesdecks.model.api.ApiCollectionCardStats;
import com.vtesdecks.model.api.ApiDeck;
import com.vtesdecks.model.api.ApiDecks;
import com.vtesdecks.model.api.ApiRuling;
import com.vtesdecks.model.api.ApiShopResult;
import com.vtesdecks.service.CurrencyExchangeService;
import com.vtesdecks.service.RulingService;
import com.vtesdecks.util.VtesUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static com.vtesdecks.util.Constants.DEFAULT_CURRENCY;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiCardInfoService {
    private final ApiCardService apiCardService;
    private final LibraryCache libraryCache;
    private final CryptCache cryptCache;
    private final ApiDeckService apiDeckService;
    private final ApiCollectionService apiCollectionService;
    private final CurrencyExchangeService currencyExchangeService;
    private final RulingService rulingService;
    private final CardErrataRepository cardErrataRepository;
    private final ApiCardErrataMapper apiCardErrataMapper;

    public ApiCardInfo getCardInfo(Integer id, String currencyCode) {
        Integer userId = ApiUtils.extractUserId();
        ApiCardInfo cardInfo = new ApiCardInfo();
        fillShopInfo(id, cardInfo);
        cardInfo.setPreconstructedDecks(getPreconstructedDecks(id));
        cardInfo.setCollectionStats(getCollectionStats(id, userId));
        fillPriceInfo(cardInfo, id, currencyCode);
        cardInfo.setRulingList(rulingService.getRulings(id));
        cardInfo.setErrataList(getCardErrata(id));
        return cardInfo;
    }

    private List<ApiCardErrata> getCardErrata(Integer id) {
        return cardErrataRepository.findByCardId(id)
                .stream()
                .map(apiCardErrataMapper::mapCardErrata)
                .sorted(Comparator.comparing(ApiCardErrata::getEffectiveDate).reversed())
                .toList();
    }

    private void fillShopInfo(Integer id, ApiCardInfo cardInfo) {
        ApiShopResult shopResult = apiCardService.getCardShops(id, false);
        if (shopResult != null) {
            cardInfo.setShopList(shopResult.getShops());
            cardInfo.setHasMoreShops(shopResult.getHasMore());
        }
    }

    public List<ApiRuling> getRulings(Integer id) {
        List<ApiRuling> rulings = rulingService.getRulings(id);
        return rulings != null ? rulings : Collections.emptyList();
    }

    private List<ApiDeck> getPreconstructedDecks(Integer id) {
        ApiDecks apiDecks = apiDeckService.getDecks(DeckType.PRECONSTRUCTED, DeckSort.NEWEST, null, null,
                null, null, null, null, null, List.of(id + "=1"), null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, 0, 10);
        return apiDecks != null && apiDecks.getDecks() != null ? apiDecks.getDecks() : Collections.emptyList();
    }

    private void fillPriceInfo(ApiCardInfo cardInfo, Integer id, String currencyCode) {
        Optional.ofNullable(VtesUtils.isCrypt(id) ? cryptCache.get(id) : libraryCache.get(id))
                .filter(item -> item.getMinPrice() != null && item.getMaxPrice() != null)
                .ifPresent(item -> {
                    if (currencyCode != null && !currencyCode.equalsIgnoreCase(DEFAULT_CURRENCY)) {
                        cardInfo.setMinPrice(currencyExchangeService.convert(item.getMinPrice(), DEFAULT_CURRENCY, currencyCode));
                        cardInfo.setMaxPrice(currencyExchangeService.convert(item.getMaxPrice(), DEFAULT_CURRENCY, currencyCode));
                        cardInfo.setCurrency(currencyCode.toUpperCase());
                    } else {
                        cardInfo.setMinPrice(item.getMinPrice());
                        cardInfo.setMaxPrice(item.getMaxPrice());
                        cardInfo.setCurrency(DEFAULT_CURRENCY);
                    }
                });
    }


    private ApiCollectionCardStats getCollectionStats(Integer id, Integer userId) {
        if (userId == null) {
            return null;
        }
        ApiCollectionCardStats collectionStats = null;
        try {
            ApiDecks decks = apiDeckService.getDecks(DeckType.USER, DeckSort.NEWEST, userId, null, null,
                    null, null, null, null, List.of(id + "=1"), null, null, null,
                    null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null,
                    null, 0, 10);
            collectionStats = apiCollectionService.getCardStats(id, decks, false);
        } catch (Exception e) {
            log.warn("Unable to fetch collection stats for card id {}", id, e);
        }
        return collectionStats;
    }

}
