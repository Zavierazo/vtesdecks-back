package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.DeckCardEntity;
import com.vtesdecks.jpa.entity.extra.DeckCardCount;
import com.vtesdecks.jpa.entity.extra.TextSearch;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DeckCardRepository extends JpaRepository<DeckCardEntity, DeckCardEntity.DeckCardId> {
    List<DeckCardEntity> findByIdDeckId(String deckId);

    @Modifying
    @Transactional
    void deleteByIdDeckId(String deckId);

    @Query(value = "SELECT dc.id as id, count(1) as number FROM deck_card dc WHERE deck_id in(SELECT id FROM deck d WHERE d.type = 'TOURNAMENT' AND d.creation_date >= current_date  - interval '2' year) GROUP BY dc.id", nativeQuery = true)
    List<DeckCardCount> selectCountByCard();


    @Query(value = "SELECT dc.id as id, sum(dc.number) as number FROM deck_card dc WHERE deck_id in(SELECT id FROM deck d WHERE d.type = 'TOURNAMENT' AND d.creation_date >= current_date  - interval '2' year) GROUP BY dc.id", nativeQuery = true)
    List<DeckCardCount> selectDeckCountByCard();

    @Query(value = "SELECT id, name, score " +
            "FROM (SELECT id, name, MATCH(name, aka) AGAINST(?1 IN NATURAL LANGUAGE MODE) AS score FROM crypt " +
            "WHERE MATCH(name, aka) AGAINST(?1 IN NATURAL LANGUAGE MODE) " +
            "AND (?2 IS NULL OR adv = ?2) " +
            "UNION ALL " +
            "SELECT id, name, MATCH(name, aka) AGAINST(?1 IN NATURAL LANGUAGE MODE) AS score FROM library " +
            "WHERE MATCH(name, aka) AGAINST(?1 IN NATURAL LANGUAGE MODE)) AS cards " +
            "WHERE cards.score > 5.0 " +
            "ORDER BY cards.score DESC",
            nativeQuery = true)
    List<TextSearch> search(String name, Boolean advanced);
}