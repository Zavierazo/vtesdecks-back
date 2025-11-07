package com.vtesdecks.scheduler;

import com.vtesdecks.jpa.entity.DeckEntity;
import com.vtesdecks.jpa.entity.DeckViewEntity;
import com.vtesdecks.jpa.repositories.DeckCardRepository;
import com.vtesdecks.jpa.repositories.DeckRepository;
import com.vtesdecks.jpa.repositories.DeckUserRepository;
import com.vtesdecks.jpa.repositories.DeckViewRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class CleanUpScheduler {
    @Autowired
    private DeckRepository deckRepository;
    @Autowired
    private DeckViewRepository deckViewRepository;
    @Autowired
    private DeckCardRepository deckCardRepository;
    @Autowired
    private DeckUserRepository deckUserRepository;

    @Scheduled(cron = "${jobs.deckViewCleanCron:0 0 2 * * *}")
    public void deckViewCleanScheduler() {
        List<DeckViewEntity> views = deckViewRepository.findOld();
        if (!CollectionUtils.isEmpty(views)) {
            Map<String, Integer> deckViews = new HashMap<>();
            //Collect views
            for (DeckViewEntity view : views) {
                String deckId = view.getId().getDeckId();
                deckViews.put(deckId, deckViews.getOrDefault(deckId, 0) + 1);
            }
            //Update deck views
            for (Map.Entry<String, Integer> deckViewEntry : deckViews.entrySet()) {
                Optional<DeckEntity> optionalDeck = deckRepository.findById(deckViewEntry.getKey());
                if (optionalDeck.isPresent()) {
                    DeckEntity deck = optionalDeck.get();
                    deck.setViews(deck.getViews() + deckViewEntry.getValue());
                    deckRepository.save(deck);
                }
            }
            //Remove old views
            for (DeckViewEntity view : views) {
                deckViewRepository.delete(view);
            }
            log.info("Cleaned {} deck views!", views.size());
        }
    }

    @Scheduled(cron = "${jobs.deckCleanCron:0 0 1 * * *}")
    public void deckCleanScheduler() {
        List<DeckEntity> decksToDelete = deckRepository.selectOldDeleted();
        if (!CollectionUtils.isEmpty(decksToDelete)) {
            for (DeckEntity deck : decksToDelete) {
                log.warn("Deleting deck forever: {}", deck);
                deckViewRepository.deleteByIdDeckId(deck.getId());
                deckCardRepository.deleteByIdDeckId(deck.getId());
                deckUserRepository.deleteByIdDeckId(deck.getId());
                deckRepository.delete(deck);
            }
        }

    }
}
