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

    @Modifying
    @Transactional
    @Query(value = "UPDATE deck_card_history " +
            "SET tag = (SELECT next_tag FROM (SELECT COALESCE(MAX(tag), 0) + 1 AS next_tag FROM deck_card_history WHERE deck_id = :deckId) t), " +
            "    tag_label = :tagLabel " +
            "WHERE id = (SELECT max_id FROM (SELECT MAX(id) AS max_id FROM deck_card_history WHERE deck_id = :deckId AND tag IS NULL) sub)",
            nativeQuery = true)
    int tagHistory(@Param("deckId") String deckId, @Param("tagLabel") String tagLabel);

    @Modifying
    @Transactional
    void deleteByDeckId(String deckId);
}
