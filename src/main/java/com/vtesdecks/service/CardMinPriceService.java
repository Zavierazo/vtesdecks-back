package com.vtesdecks.service;

import com.vtesdecks.jpa.entity.CardMinPriceEntity;
import com.vtesdecks.jpa.repositories.CardMinPriceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Persists the minimum card price computed by the card caches so SQL queries can sort by the
 * exact same price the user sees. Only changed rows are written on each sync.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CardMinPriceService {
    private final CardMinPriceRepository cardMinPriceRepository;

    public void syncMinPrices(Map<Integer, BigDecimal> minPriceByCardId) {
        try {
            Map<Integer, CardMinPriceEntity> existing = new HashMap<>();
            cardMinPriceRepository.findAllById(minPriceByCardId.keySet())
                    .forEach(entity -> existing.put(entity.getCardId(), entity));
            List<CardMinPriceEntity> changed = new ArrayList<>();
            minPriceByCardId.forEach((cardId, minPrice) -> {
                CardMinPriceEntity entity = existing.get(cardId);
                if (entity == null) {
                    changed.add(CardMinPriceEntity.builder().cardId(cardId).minPrice(minPrice).build());
                } else if (priceChanged(entity.getMinPrice(), minPrice)) {
                    entity.setMinPrice(minPrice);
                    changed.add(entity);
                }
            });
            if (!changed.isEmpty()) {
                cardMinPriceRepository.saveAll(changed);
                log.info("Updated min price for {} cards", changed.size());
            }
        } catch (Exception e) {
            log.error("Error synchronizing card min prices", e);
        }
    }

    public void deleteMinPrices(Collection<Integer> cardIds) {
        try {
            cardMinPriceRepository.deleteAllById(cardIds);
        } catch (Exception e) {
            log.error("Error deleting card min prices {}", cardIds, e);
        }
    }

    private static boolean priceChanged(BigDecimal current, BigDecimal candidate) {
        if (current == null || candidate == null) {
            return current != candidate;
        }
        return current.compareTo(candidate) != 0;
    }
}
