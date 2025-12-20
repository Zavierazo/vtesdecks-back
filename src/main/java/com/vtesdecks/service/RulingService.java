package com.vtesdecks.service;

import com.vtesdecks.api.mapper.ApiCardInfoMapper;
import com.vtesdecks.cache.redis.entity.CardRuling;
import com.vtesdecks.cache.redis.repositories.CardRulingRepository;
import com.vtesdecks.integration.KRCGClient;
import com.vtesdecks.model.api.ApiRuling;
import com.vtesdecks.model.krcg.Card;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RulingService {
    private final KRCGClient krcgClient;
    private final ApiCardInfoMapper apiCardInfoMapper;
    private final CardRulingRepository cardRulingRepository;

    public List<ApiRuling> getRulings(Integer id) {
        CardRuling cardRuling = getCardRuling(id);
        return cardRuling != null ? cardRuling.getRulings() : Collections.emptyList();
    }

    private CardRuling getCardRuling(Integer id) {
        Optional<CardRuling> cardRuling = cardRulingRepository.findById(id);
        if (cardRuling.isPresent()) {
            return cardRuling.get();
        } else {
            CardRuling newCardRuling = CardRuling.builder()
                    .id(id)
                    .rulings(getKRCGRulings(id))
                    .build();
            cardRulingRepository.save(newCardRuling);
            return newCardRuling;
        }
    }

    private List<ApiRuling> getKRCGRulings(Integer id) {
        Card card = null;
        try {
            card = krcgClient.getCard(id);
        } catch (FeignException.NotFound e) {
            log.warn("Card id {} not found in KRCG", id);
        } catch (Exception e) {
            log.warn("Unable to fetch card rulings from KRCG for card id {}", id, e);
        }
        return card != null ? apiCardInfoMapper.mapRulings(card.getRulings()) : Collections.emptyList();
    }

}
