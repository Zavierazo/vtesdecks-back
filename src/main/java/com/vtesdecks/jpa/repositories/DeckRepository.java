package com.vtesdecks.jpa.repositories;

import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.jpa.entity.DeckEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.time.LocalDateTime;

public interface DeckRepository extends JpaRepository<DeckEntity, String> {

    // Preserve the timestamp explicitly because MySQL also has ON UPDATE CURRENT_TIMESTAMP.
    @Modifying
    @Query(value = "UPDATE deck SET views = views + :views, modification_date = modification_date WHERE id = :id", nativeQuery = true)
    int incrementViews(@Param("id") String id, @Param("views") int views);

    List<DeckEntity> findByTypeAndEventIdAndPositionAndDeletedFalse(DeckType type, String eventId, Integer position);

    boolean existsByIdAndUser(String id, Integer user);

    @Query(value = "SELECT * FROM deck WHERE type = 'TOURNAMENT' AND deleted = false"
            + " AND creation_date >= :start AND creation_date < :end", nativeQuery = true)
    List<DeckEntity> findActiveTournamentDecksBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Modifying
    @Query(value = "UPDATE deck SET vekn_id = :veknId, modification_date = modification_date"
            + " WHERE id = :id AND type = 'TOURNAMENT' AND deleted = false"
            + " AND (vekn_id IS NULL OR TRIM(vekn_id) = '')", nativeQuery = true)
    int fillMissingVeknId(@Param("id") String id, @Param("veknId") String veknId);

    @Query(value = "SELECT * FROM deck WHERE type ='COMMUNITY' AND deleted = true AND modification_date < (NOW() - INTERVAL 60 DAY)", nativeQuery = true)
    List<DeckEntity> selectOldDeleted();

    @Query(value = "SELECT * FROM deck WHERE type ='COMMUNITY' AND deleted = true AND user=:userId ORDER BY modification_date DESC", nativeQuery = true)
    List<DeckEntity> selectUserDeleted(Integer userId);

}
