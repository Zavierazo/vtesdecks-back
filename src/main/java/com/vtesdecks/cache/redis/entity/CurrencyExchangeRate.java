package com.vtesdecks.cache.redis.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RedisHash(value = "CurrencyExchangeRate", timeToLive = 43200) // 12 hours
public class CurrencyExchangeRate {
    @Id
    private String id;
    private BigDecimal rate;
}
