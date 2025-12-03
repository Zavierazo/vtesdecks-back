package com.vtesdecks.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Scheduler;
import com.vtesdecks.integration.CurrencyExchangeClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class CurrencyExchangeService {
    private static final int SCALE = 2;
    private final CurrencyExchangeClient currencyExchangeClient;
    private final LoadingCache<@NonNull Pair<String, String>, BigDecimal> exchangeRates = Caffeine.newBuilder()
            .maximumSize(10)
            .expireAfterWrite(Duration.ofHours(12))
            .scheduler(Scheduler.systemScheduler())
            .build(this::getExchangeRate);

    public BigDecimal convert(BigDecimal value, String sourceCurrency, String targetCurrency) {
        BigDecimal exchangeRate = exchangeRates.get(Pair.of(sourceCurrency, targetCurrency));
        return value.multiply(exchangeRate).setScale(SCALE, RoundingMode.UP);
    }

    private BigDecimal getExchangeRate(Pair<String, String> key) {
        return getExchangeRate(key.getLeft(), key.getRight());
    }

    private BigDecimal getExchangeRate(String sourceCurrency, String targetCurrency) {
        try {
            String exchangeRate = currencyExchangeClient.getLatest(sourceCurrency, targetCurrency);
            if (StringUtils.isNotBlank(exchangeRate)) {
                return new BigDecimal(exchangeRate);
            } else {
                log.warn("Could not get exchange rate from {} to {}", sourceCurrency, targetCurrency);
            }
        } catch (Exception e) {
            log.warn("Could not get exchange rate from {} to {}", sourceCurrency, targetCurrency, e);
        }
        return BigDecimal.ONE;
    }
}
