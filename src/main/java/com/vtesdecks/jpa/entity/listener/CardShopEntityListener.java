package com.vtesdecks.jpa.entity.listener;

import com.vtesdecks.jpa.entity.CardShopEntity;
import com.vtesdecks.service.CurrencyExchangeService;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

import static com.vtesdecks.util.Constants.DEFAULT_CURRENCY;

/**
 * Keeps card_shop.price_default_currency in sync on every write, so queries can sort by price
 * in a single currency without converting at query time.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CardShopEntityListener {
    private final CurrencyExchangeService currencyExchangeService;

    @PrePersist
    @PreUpdate
    public void updateDefaultCurrencyPrice(CardShopEntity cardShop) {
        cardShop.setPriceDefaultCurrency(getDefaultCurrencyPrice(cardShop));
    }

    private BigDecimal getDefaultCurrencyPrice(CardShopEntity cardShop) {
        if (cardShop.getPrice() == null) {
            return null;
        }
        if (cardShop.getCurrency() == null || DEFAULT_CURRENCY.equals(cardShop.getCurrency())) {
            return cardShop.getPrice();
        }
        try {
            return currencyExchangeService.convert(cardShop.getPrice(), cardShop.getCurrency(), DEFAULT_CURRENCY);
        } catch (Exception e) {
            log.warn("Unable to convert price {} {} to {} for card {}", cardShop.getPrice(), cardShop.getCurrency(), DEFAULT_CURRENCY, cardShop.getCardId(), e);
            return null;
        }
    }
}
