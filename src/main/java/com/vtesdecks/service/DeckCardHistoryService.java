package com.vtesdecks.service;

import com.vtesdecks.jpa.entity.DeckCardHistoryEntity;
import com.vtesdecks.jpa.repositories.DeckCardHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeckCardHistoryService {

    private final DeckCardHistoryRepository deckCardHistoryRepository;

    /**
     * Get all history for a specific deck ordered by id ascending (chronological, for event-sourcing)
     */
    public List<DeckCardHistoryEntity> getDeckHistoryAsc(String deckId) {
        return deckCardHistoryRepository.findByDeckIdOrderByIdAsc(deckId);
    }

    /**
     * Get the max history row id for a deck (used as cursor before a save)
     */
    public Long getMaxId(String deckId) {
        return deckCardHistoryRepository.findMaxIdByDeckId(deckId);
    }

    /**
     * Get the max tag value for a deck (used to compute next sequential tag)
     */
    public Integer getMaxTag(String deckId) {
        return deckCardHistoryRepository.findMaxTagByDeckId(deckId);
    }

    /**
     * Tag the last history row written after minId as a named save point
     */
    @Transactional
    public int tagLastEntry(String deckId, Long minId, Integer tag, String tagLabel) {
        return deckCardHistoryRepository.tagHistory(deckId, minId, tag, tagLabel);
    }
}
