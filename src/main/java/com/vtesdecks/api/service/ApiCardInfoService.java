package com.vtesdecks.api.service;

import com.vtesdecks.api.mapper.ApiCardInfoMapper;
import com.vtesdecks.api.util.ApiUtils;
import com.vtesdecks.integration.KRCGClient;
import com.vtesdecks.model.DeckSort;
import com.vtesdecks.model.DeckType;
import com.vtesdecks.model.api.ApiCardInfo;
import com.vtesdecks.model.api.ApiCollectionCardStats;
import com.vtesdecks.model.api.ApiDecks;
import com.vtesdecks.model.krcg.Card;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiCardInfoService {
    private final ApiCardService apiCardService;
    private final ApiDeckService apiDeckService;
    private final ApiCollectionService apiCollectionService;
    private final KRCGClient krcgClient;
    private final ApiCardInfoMapper apiCardInfoMapper;

    public ApiCardInfo getCardInfo(Integer id) {
        ApiCardInfo cardInfo = new ApiCardInfo();
        cardInfo.setShopList(apiCardService.getCardShops(id, false));
        ApiDecks apiDecks = apiDeckService.getDecks(DeckType.PRECONSTRUCTED, DeckSort.NEWEST, null, null,
                null, null, null, null, List.of(id + "=1"), null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, 0, 10);
        cardInfo.setPreconstructedDecks(apiDecks != null && apiDecks.getDecks() != null ? apiDecks.getDecks() : Collections.emptyList());
        Card card = getKRCGRulings(id);
        if (card != null) {
            cardInfo.setRulingList(apiCardInfoMapper.mapRulings(card.getRulings()));
        }
        if (ApiUtils.extractUserId() != null) {
            cardInfo.setCollectionStats(getCollectionStats(id));
        }
        return cardInfo;
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
            ApiDecks decks = apiDeckService.getDecks(DeckType.USER, DeckSort.NEWEST, ApiUtils.extractUserId(), null, null, null,
                    null, null, List.of(id + "=1"), null, null, null, null,
                    null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, 0, 10);
            collectionStats = apiCollectionService.getCardStats(id, decks, false);
        } catch (Exception e) {
            log.warn("Unable to fetch collection stats for card id {}", id, e);
        }
        return collectionStats;
    }

}
