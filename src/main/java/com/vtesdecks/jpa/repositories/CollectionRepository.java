package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.CollectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CollectionRepository extends JpaRepository<CollectionEntity, Integer> {
    List<CollectionEntity> findByUserIdAndDeletedFalse(Integer userId);

    @Query(value = "SELECT * FROM collection WHERE deleted = true AND modification_date < (NOW() - INTERVAL 60 DAY)", nativeQuery = true)
    List<CollectionEntity> selectOldDeleted();
}
