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
     * Tag the last untagged history row of this deck as a named save point.
     * The minId cursor and next sequential tag number are resolved directly in the query.
     */
    @Transactional
    public int tagLastEntry(String deckId, String tagLabel) {
        return deckCardHistoryRepository.tagHistory(deckId, tagLabel);
    }
}
