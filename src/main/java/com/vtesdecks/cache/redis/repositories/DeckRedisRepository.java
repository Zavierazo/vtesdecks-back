package com.vtesdecks.cache.redis.repositories;

import com.vtesdecks.cache.indexable.Deck;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeckRedisRepository extends CrudRepository<Deck, String> {
}
