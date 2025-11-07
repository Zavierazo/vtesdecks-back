package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.CollectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CollectionRepository extends JpaRepository<CollectionEntity, Integer> {
    List<CollectionEntity> findByUserIdAndDeletedFalse(Integer userId);
}