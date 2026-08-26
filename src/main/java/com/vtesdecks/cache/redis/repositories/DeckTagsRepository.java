package com.vtesdecks.cache.redis.repositories;

import com.vtesdecks.cache.redis.entity.DeckTags;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeckTagsRepository extends CrudRepository<DeckTags, String> {
}
