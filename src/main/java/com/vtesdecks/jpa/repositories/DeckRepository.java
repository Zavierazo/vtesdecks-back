package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.DeckEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DeckRepository extends JpaRepository<DeckEntity, String> {

    @Query(value = "SELECT * FROM deck WHERE type ='COMMUNITY' AND deleted = true AND modification_date < (NOW() - INTERVAL 60 DAY)", nativeQuery = true)
    List<DeckEntity> selectOldDeleted();

    @Query(value = "SELECT * FROM deck WHERE type ='COMMUNITY' AND deleted = true AND user=:userId", nativeQuery = true)
    List<DeckEntity> selectUserDeleted(Integer userId);

}