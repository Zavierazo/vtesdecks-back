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
import com.vtesdecks.model.api.ApiDeck;
import com.vtesdecks.model.api.ApiDecks;
import com.vtesdecks.model.api.ApiRuling;
import com.vtesdecks.model.krcg.Card;
import com.vtesdecks.service.CurrencyExchangeService;
import com.vtesdecks.util.VtesUtils;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

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

        Integer userId = ApiUtils.extractUserId();

        // Execute independent remote operations
        CompletableFuture<List<ApiRuling>> rulingsFuture = CompletableFuture.supplyAsync(() -> getRulings(id));

        // Set results (join() will wait for each future to complete)
        ApiCardInfo cardInfo = new ApiCardInfo();
        cardInfo.setShopList(apiCardService.getCardShops(id, false));
        cardInfo.setPreconstructedDecks(getPreconstructedDecks(id));
        cardInfo.setCollectionStats(getCollectionStats(id, userId));
        fillPriceInfo(cardInfo, id, currencyCode);
        cardInfo.setRulingList(rulingsFuture.join());
        return cardInfo;
    }

    private List<ApiDeck> getPreconstructedDecks(Integer id) {
        ApiDecks apiDecks = apiDeckService.getDecks(DeckType.PRECONSTRUCTED, DeckSort.NEWEST, null, null,
                null, null, null, null, null, List.of(id + "=1"), null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null,
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

    private List<ApiRuling> getRulings(Integer id) {
        Card card = null;
        try {
            card = krcgClient.getCard(id);
        } catch (FeignException.NotFound e) {
            log.warn("Card id {} not found in KRCG", id);
        } catch (Exception e) {
            log.warn("Unable to fetch card rulings from KRCG for card id {}", id, e);
        }
        return card != null ? apiCardInfoMapper.mapRulings(card.getRulings()) : null;
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
                    null, null, null, null, null, null,
                    null, 0, 10);
            collectionStats = apiCollectionService.getCardStats(id, decks, false);
        } catch (Exception e) {
            log.warn("Unable to fetch collection stats for card id {}", id, e);
        }
        return collectionStats;
    }

}
