package com.vtesdecks.jpa.repositories;

import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.jpa.entity.DeckEntity;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DeckRepository extends JpaRepository<DeckEntity, String> {

    List<DeckEntity> findByTypeAndNameContainingIgnoreCase(DeckType type, String name);

    @Query(value = "SELECT * FROM deck WHERE type ='COMMUNITY' AND deleted = true AND modification_date < (NOW() - INTERVAL 60 DAY)", nativeQuery = true)
    List<DeckEntity> selectOldDeleted();

    @Query(value = "SELECT * FROM deck WHERE type ='COMMUNITY' AND deleted = true AND user=:userId ORDER BY modification_date DESC", nativeQuery = true)
    List<DeckEntity> selectUserDeleted(Integer userId);

    @Query(value = "SELECT id FROM deck WHERE type = 'TOURNAMENT' AND verified = false AND deleted = false"
            + " AND modification_date < (NOW() - INTERVAL 1 MONTH)"
            + " AND NOT EXISTS (SELECT 1 FROM deck_card dc WHERE dc.deck_id = deck.id"
            + " AND dc.modification_date >= (NOW() - INTERVAL 1 MONTH))", nativeQuery = true)
    List<String> selectStaleUnverifiedTournamentIds();

    //modification_date is assigned to itself so ON UPDATE CURRENT_TIMESTAMP does not bump it
    @Modifying
    @Transactional
    @Query(value = "UPDATE deck SET verified = true, modification_date = modification_date WHERE id IN (:ids)", nativeQuery = true)
    void markAsVerified(List<String> ids);

}