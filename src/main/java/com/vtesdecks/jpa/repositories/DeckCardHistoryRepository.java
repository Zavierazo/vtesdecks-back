package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.DeckCardHistoryEntity;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DeckCardHistoryRepository extends JpaRepository<DeckCardHistoryEntity, Long> {

    List<DeckCardHistoryEntity> findByDeckIdOrderByIdAsc(String deckId);

    @Query("SELECT MAX(h.id) FROM DeckCardHistoryEntity h WHERE h.deckId = :deckId")
    Long findMaxIdByDeckId(@Param("deckId") String deckId);

    @Query("SELECT MAX(h.tag) FROM DeckCardHistoryEntity h WHERE h.deckId = :deckId")
    Integer findMaxTagByDeckId(@Param("deckId") String deckId);

    @Modifying
    @Transactional
    @Query(value = "UPDATE deck_card_history SET tag = :tag, tag_label = :tagLabel " +
            "WHERE id = (SELECT max_id FROM (SELECT MAX(id) AS max_id FROM deck_card_history WHERE deck_id = :deckId AND id > :minId AND tag IS NULL) sub)",
            nativeQuery = true)
    int tagHistory(@Param("deckId") String deckId, @Param("minId") Long minId, @Param("tag") Integer tag, @Param("tagLabel") String tagLabel);

    @Modifying
    @Transactional
    void deleteByDeckId(String deckId);
}
