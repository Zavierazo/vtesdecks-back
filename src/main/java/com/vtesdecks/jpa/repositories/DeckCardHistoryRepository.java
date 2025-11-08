package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.DeckCardHistoryEntity;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DeckCardHistoryRepository extends JpaRepository<DeckCardHistoryEntity, Long> {

    List<DeckCardHistoryEntity> findByDeckIdOrderByCreationDateDesc(String deckId);

    @Query("SELECT h FROM DeckCardHistoryEntity h WHERE h.deckId = :deckId AND h.tag IS NOT NULL ORDER BY h.creationDate DESC")
    List<DeckCardHistoryEntity> findTaggedHistoryByDeckId(@Param("deckId") String deckId);

    @Modifying
    @Transactional
    void deleteByDeckId(String deckId);
}
