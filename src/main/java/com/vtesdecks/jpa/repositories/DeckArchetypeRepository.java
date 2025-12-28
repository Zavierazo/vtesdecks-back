package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.DeckArchetypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeckArchetypeRepository extends JpaRepository<DeckArchetypeEntity, Integer> {

    List<DeckArchetypeEntity> findByNameContainingIgnoreCase(String name);
}

