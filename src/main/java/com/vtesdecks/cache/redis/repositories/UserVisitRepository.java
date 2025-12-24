package com.vtesdecks.cache.redis.repositories;

import com.vtesdecks.cache.redis.entity.UserVisit;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserVisitRepository extends CrudRepository<UserVisit, String> {
    List<UserVisit> findByUserId(Integer userId);
}