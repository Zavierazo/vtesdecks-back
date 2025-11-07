package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.DeckViewEntity;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DeckViewRepository extends JpaRepository<DeckViewEntity, DeckViewEntity.DeckViewId> {

    @Query(value = "SELECT * FROM deck_view WHERE modification_date < (NOW() - INTERVAL 60 DAY)", nativeQuery = true)
    List<DeckViewEntity> findOld();

    List<DeckViewEntity> findByIdDeckId(String deckId);

    @Modifying
    @Transactional
    void deleteByIdDeckId(String deckId);
}