package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.CardShopEntity;
import com.vtesdecks.model.ShopPlatform;
import com.vtesdecks.service.CurrencyExchangeService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.vtesdecks.util.Constants.DEFAULT_CURRENCY;

/**
 * Builds the ORDER BY price expression so that sorting matches the price shown to the user
 * (see LibraryFactory/CryptFactory minPrice): only enabled platforms, prices converted to the
 * default currency and in-stock offers preferred, falling back to any offer when none is in stock.
 */
@Component
@RequiredArgsConstructor
public class CardShopMinPriceCriteria {
    private static final List<ShopPlatform> ENABLED_PLATFORMS = Arrays.stream(ShopPlatform.values())
            .filter(ShopPlatform::isEnabled)
            .toList();

    private final CurrencyExchangeService currencyExchangeService;

    @PersistenceContext
    private EntityManager em;

    public Expression<BigDecimal> minPriceExpression(CriteriaQuery<?> cq, CriteriaBuilder cb, Expression<Integer> cardIdExpression) {
        Map<String, BigDecimal> exchangeRates = getExchangeRates();
        Subquery<BigDecimal> inStockMinPrice = minPriceSubquery(cq, cb, cardIdExpression, exchangeRates, true);
        Subquery<BigDecimal> anyMinPrice = minPriceSubquery(cq, cb, cardIdExpression, exchangeRates, false);
        return cb.coalesce(inStockMinPrice, anyMinPrice);
    }

    private Subquery<BigDecimal> minPriceSubquery(CriteriaQuery<?> cq, CriteriaBuilder cb, Expression<Integer> cardIdExpression,
                                                  Map<String, BigDecimal> exchangeRates, boolean onlyInStock) {
        Subquery<BigDecimal> subquery = cq.subquery(BigDecimal.class);
        Root<CardShopEntity> shopRoot = subquery.from(CardShopEntity.class);
        subquery.select(cb.min(toDefaultCurrency(cb, shopRoot, exchangeRates)));
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(shopRoot.get("cardId"), cardIdExpression));
        predicates.add(shopRoot.get("platform").in(ENABLED_PLATFORMS));
        if (onlyInStock) {
            predicates.add(cb.isTrue(shopRoot.get("inStock")));
        }
        subquery.where(predicates.toArray(new Predicate[0]));
        return subquery;
    }

    private Expression<BigDecimal> toDefaultCurrency(CriteriaBuilder cb, Root<CardShopEntity> shopRoot, Map<String, BigDecimal> exchangeRates) {
        Path<BigDecimal> price = shopRoot.get("price");
        if (exchangeRates.isEmpty()) {
            return price;
        }
        CriteriaBuilder.Case<BigDecimal> conversion = cb.selectCase();
        for (Map.Entry<String, BigDecimal> exchangeRate : exchangeRates.entrySet()) {
            conversion = conversion.when(cb.equal(shopRoot.get("currency"), exchangeRate.getKey()), cb.prod(price, exchangeRate.getValue()));
        }
        return conversion.otherwise(price);
    }

    private Map<String, BigDecimal> getExchangeRates() {
        return em.createQuery("select distinct c.currency from CardShopEntity c where c.currency is not null and c.currency <> :defaultCurrency", String.class)
                .setParameter("defaultCurrency", DEFAULT_CURRENCY)
                .getResultList().stream()
                .collect(Collectors.toMap(Function.identity(), currency -> currencyExchangeService.getRate(currency, DEFAULT_CURRENCY)));
    }
}
