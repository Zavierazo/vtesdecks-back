package com.vtesdecks.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Scheduler;
import com.vtesdecks.api.mapper.ApiCardInfoMapper;
import com.vtesdecks.integration.KRCGClient;
import com.vtesdecks.model.api.ApiRuling;
import com.vtesdecks.model.krcg.Card;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RulingService {
    private final KRCGClient krcgClient;
    private final ApiCardInfoMapper apiCardInfoMapper;
    private final LoadingCache<@NonNull Integer, List<ApiRuling>> rulingsCache = Caffeine.newBuilder()
            .refreshAfterWrite(Duration.ofHours(12))
            .scheduler(Scheduler.systemScheduler())
            .build(this::getKRCGRulings);


    public List<ApiRuling> getRulings(Integer id) {
        return rulingsCache.get(id);
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
