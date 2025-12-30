package com.vtesdecks.cache.redis.repositories;

import com.vtesdecks.cache.redis.entity.DeckArchetype;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DeckArchetypeRedisRepository extends CrudRepository<DeckArchetype, Integer> {
    Optional<DeckArchetype> findByDeckId(String deckId);
}