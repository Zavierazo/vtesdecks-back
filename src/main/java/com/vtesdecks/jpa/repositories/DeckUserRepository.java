package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.DeckUserEntity;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;

public interface DeckUserRepository extends JpaRepository<DeckUserEntity, DeckUserEntity.DeckUserId> {
    List<DeckUserEntity> findByIdDeckId(String deckId);

    List<DeckUserEntity> findFavoriteTrueByIdUserOrderByModificationDateDesc(Integer user);

    @Modifying
    @Transactional
    void deleteByIdDeckId(String deckId);
}