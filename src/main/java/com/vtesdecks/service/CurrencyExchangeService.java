package com.vtesdecks.service;

import com.vtesdecks.cache.redis.entity.CurrencyExchangeRate;
import com.vtesdecks.cache.redis.repositories.CurrencyExchangeRateRepository;
import com.vtesdecks.integration.CurrencyExchangeClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CurrencyExchangeService {
    private static final int SCALE = 2;
    private final CurrencyExchangeClient currencyExchangeClient;
    private final CurrencyExchangeRateRepository currencyExchangeRateRepository;

    public BigDecimal convert(BigDecimal value, String fromCurrency, String toCurrency) {
        BigDecimal exchangeRate = getExchangeRate(fromCurrency, toCurrency);
        return value.multiply(exchangeRate).setScale(SCALE, RoundingMode.UP);
    }

    private BigDecimal getExchangeRate(String fromCurrency, String toCurrency) {
        String id = String.format("%s_%s", fromCurrency, toCurrency);
        Optional<CurrencyExchangeRate> exchangeRate = currencyExchangeRateRepository.findById(id);
        if (exchangeRate.isPresent()) {
            return exchangeRate.get().getRate();
        }
        BigDecimal rate = getExchangeRateFromApi(fromCurrency, toCurrency);
        currencyExchangeRateRepository.save(
                CurrencyExchangeRate.builder()
                        .id(id)
                        .rate(rate)
                        .build()
        );
        return rate;
    }

    private BigDecimal getExchangeRateFromApi(String sourceCurrency, String targetCurrency) {
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
