package com.vtesdecks.cache.redis.repositories;

import com.vtesdecks.cache.redis.entity.ProxyCardOption;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProxyCardOptionRepository extends CrudRepository<ProxyCardOption, Integer> {
}