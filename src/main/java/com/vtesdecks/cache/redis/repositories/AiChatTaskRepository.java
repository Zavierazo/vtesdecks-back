package com.vtesdecks.cache.redis.repositories;

import com.vtesdecks.cache.redis.entity.AiChatTask;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiChatTaskRepository extends CrudRepository<AiChatTask, String> {
}