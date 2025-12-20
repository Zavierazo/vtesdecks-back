package com.vtesdecks.cache.redis.repositories;

import com.vtesdecks.cache.redis.entity.CardRuling;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CardRulingRepository extends CrudRepository<CardRuling, Integer> {
}