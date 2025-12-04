package com.vtesdecks.api.service;

import com.vtesdecks.api.mapper.ApiCardInfoMapper;
import com.vtesdecks.api.util.ApiUtils;
import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.integration.KRCGClient;
import com.vtesdecks.model.DeckSort;
import com.vtesdecks.model.DeckType;
import com.vtesdecks.model.api.ApiCardInfo;
import com.vtesdecks.model.api.ApiCollectionCardStats;
import com.vtesdecks.model.api.ApiDecks;
import com.vtesdecks.model.krcg.Card;
import com.vtesdecks.service.CurrencyExchangeService;
import com.vtesdecks.util.VtesUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
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
    private final KRCGClient krcgClient;
    private final ApiCardInfoMapper apiCardInfoMapper;
    private final CurrencyExchangeService currencyExchangeService;

    public ApiCardInfo getCardInfo(Integer id, String currencyCode) {
        ApiCardInfo cardInfo = new ApiCardInfo();
        cardInfo.setShopList(apiCardService.getCardShops(id, false));
        ApiDecks apiDecks = apiDeckService.getDecks(DeckType.PRECONSTRUCTED, DeckSort.NEWEST, null, null,
                null, null, null, null, null, List.of(id + "=1"), null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null,
                null, 0, 10);
        cardInfo.setPreconstructedDecks(apiDecks != null && apiDecks.getDecks() != null ? apiDecks.getDecks() : Collections.emptyList());
        Card card = getKRCGRulings(id);
        if (card != null) {
            cardInfo.setRulingList(apiCardInfoMapper.mapRulings(card.getRulings()));
        }
        if (ApiUtils.extractUserId() != null) {
            cardInfo.setCollectionStats(getCollectionStats(id));
        }
        fillPriceInfo(id, currencyCode, cardInfo);
        return cardInfo;
    }

    private void fillPriceInfo(Integer id, String currencyCode, ApiCardInfo cardInfo) {
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

    private Card getKRCGRulings(Integer id) {
        Card card = null;
        try {
            card = krcgClient.getCard(id);
        } catch (Exception e) {
            log.warn("Unable to fetch card rulings from KRCG for card id {}", id, e);
        }
        return card;
    }

    private ApiCollectionCardStats getCollectionStats(Integer id) {
        ApiCollectionCardStats collectionStats = null;
        try {
            ApiDecks decks = apiDeckService.getDecks(DeckType.USER, DeckSort.NEWEST, ApiUtils.extractUserId(), null, null,
                    null, null, null, null, List.of(id + "=1"), null, null, null,
                    null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null,
                    null, 0, 10);
            collectionStats = apiCollectionService.getCardStats(id, decks, false);
        } catch (Exception e) {
            log.warn("Unable to fetch collection stats for card id {}", id, e);
        }
        return collectionStats;
    }

}
