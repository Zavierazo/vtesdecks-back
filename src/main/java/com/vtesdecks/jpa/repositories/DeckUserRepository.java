package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.DeckUserEntity;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface DeckUserRepository extends JpaRepository<DeckUserEntity, DeckUserEntity.DeckUserId> {
    @Query(value = "SELECT * FROM deck_user WHERE deck_id = :deckId AND (rate IS NOT NULL OR favorite = 1)", nativeQuery = true)
    List<DeckUserEntity> findEngagedByDeckId(@Param("deckId") String deckId);

    @Query("SELECT deckUser FROM DeckUserEntity deckUser WHERE deckUser.id.user = :userId AND deckUser.id.deckId IN :deckIds")
    List<DeckUserEntity> findByIdUserAndIdDeckIdIn(@Param("userId") Integer userId,
                                                   @Param("deckIds") Collection<String> deckIds);

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO deck_user (`user`, deck_id)
            VALUES (:userId, :deckId)
            ON DUPLICATE KEY UPDATE modification_date = CURRENT_TIMESTAMP
            """, nativeQuery = true)
    void recordVisit(@Param("userId") Integer userId, @Param("deckId") String deckId);

    List<DeckUserEntity> findFavoriteTrueByIdUserOrderByModificationDateDesc(Integer user);

    @Modifying
    @Transactional
    void deleteByIdDeckId(String deckId);
}
