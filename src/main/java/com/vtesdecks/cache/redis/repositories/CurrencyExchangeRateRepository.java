package com.vtesdecks.cache.redis.repositories;

import com.vtesdecks.cache.redis.entity.CurrencyExchangeRate;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CurrencyExchangeRateRepository extends CrudRepository<CurrencyExchangeRate, String> {
}