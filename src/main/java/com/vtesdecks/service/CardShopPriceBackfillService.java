package com.vtesdecks.service;

import com.vtesdecks.jpa.entity.CardShopEntity;
import com.vtesdecks.jpa.entity.listener.CardShopEntityListener;
import com.vtesdecks.jpa.repositories.CardShopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * One-shot backfill of card_shop.price_default_currency for rows written before the column
 * existed (or whose conversion failed). New writes are covered by CardShopEntityListener.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CardShopPriceBackfillService {
    private final CardShopRepository cardShopRepository;
    private final CardShopEntityListener cardShopEntityListener;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void backfillDefaultCurrencyPrices() {
        try {
            List<CardShopEntity> pending = cardShopRepository.findByPriceIsNotNullAndPriceDefaultCurrencyIsNull();
            if (pending.isEmpty()) {
                return;
            }
            pending.forEach(cardShopEntityListener::updateDefaultCurrencyPrice);
            // Rows whose conversion failed stay null and are retried on the next startup
            List<CardShopEntity> converted = pending.stream()
                    .filter(cardShop -> cardShop.getPriceDefaultCurrency() != null)
                    .toList();
            cardShopRepository.saveAll(converted);
            log.info("Backfilled default currency price for {} of {} card shop rows", converted.size(), pending.size());
        } catch (Exception e) {
            log.error("Error backfilling card shop default currency prices", e);
        }
    }
}
