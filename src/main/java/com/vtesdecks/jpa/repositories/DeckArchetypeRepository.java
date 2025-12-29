package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.DeckArchetypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeckArchetypeRepository extends JpaRepository<DeckArchetypeEntity, Integer> {

    Optional<DeckArchetypeEntity> findByDeckId(String deckId);

    List<DeckArchetypeEntity> findByEnabledTrue();

}
