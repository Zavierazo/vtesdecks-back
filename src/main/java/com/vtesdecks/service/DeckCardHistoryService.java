package com.vtesdecks.service;

import com.vtesdecks.jpa.entity.DeckCardHistoryEntity;
import com.vtesdecks.jpa.repositories.DeckCardHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DeckCardHistoryService {

    private final DeckCardHistoryRepository deckCardHistoryRepository;

    /**
     * Get all history for a specific deck ordered by creation date descending
     */
    public List<DeckCardHistoryEntity> getDeckHistory(String deckId) {
        return deckCardHistoryRepository.findByDeckIdOrderByCreationDateDesc(deckId);
    }

    /**
     * Get all tagged history entries for a deck
     */
    public List<DeckCardHistoryEntity> getTaggedHistory(String deckId) {
        return deckCardHistoryRepository.findTaggedHistoryByDeckId(deckId);
    }

    /**
     * Add a tag to a specific history entry
     */
    @Transactional
    public void addTag(Long historyId, Integer tag) {
        Optional<DeckCardHistoryEntity> historyEntry = deckCardHistoryRepository.findById(historyId);
        if (historyEntry.isPresent()) {
            DeckCardHistoryEntity entity = historyEntry.get();
            entity.setTag(tag);
            deckCardHistoryRepository.save(entity);
        }
    }

    /**
     * Remove tag from a specific history entry
     */
    @Transactional
    public void removeTag(Long historyId) {
        Optional<DeckCardHistoryEntity> historyEntry = deckCardHistoryRepository.findById(historyId);
        if (historyEntry.isPresent()) {
            DeckCardHistoryEntity entity = historyEntry.get();
            entity.setTag(null);
            deckCardHistoryRepository.save(entity);
        }
    }
}
