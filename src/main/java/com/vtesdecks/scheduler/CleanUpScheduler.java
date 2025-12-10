package com.vtesdecks.scheduler;

import com.vtesdecks.jpa.entity.DeckEntity;
import com.vtesdecks.jpa.entity.DeckViewEntity;
import com.vtesdecks.jpa.repositories.DeckCardHistoryRepository;
import com.vtesdecks.jpa.repositories.DeckCardRepository;
import com.vtesdecks.jpa.repositories.DeckRepository;
import com.vtesdecks.jpa.repositories.DeckUserRepository;
import com.vtesdecks.jpa.repositories.DeckViewRepository;
import com.vtesdecks.service.AiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class CleanUpScheduler {
    private final DeckRepository deckRepository;
    private final DeckViewRepository deckViewRepository;
    private final DeckCardRepository deckCardRepository;
    private final DeckUserRepository deckUserRepository;
    private final DeckCardHistoryRepository deckCardHistoryRepository;
    private final AiService aiService;

    @Scheduled(cron = "${jobs.deckViewCleanCron:0 0 2 * * *}")
    @Transactional
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
                    deckRepository.saveAndFlush(deck);
                }
            }
            //Remove old views
            deckViewRepository.deleteAll(views);
            deckViewRepository.flush();
            log.info("Cleaned {} deck views!", views.size());
        }
    }

    @Scheduled(cron = "${jobs.deckCleanCron:0 0 1 * * *}")
    @Transactional
    public void deckCleanScheduler() {
        List<DeckEntity> decksToDelete = deckRepository.selectOldDeleted();
        if (!CollectionUtils.isEmpty(decksToDelete)) {
            for (DeckEntity deck : decksToDelete) {
                log.warn("Deleting deck forever: {}", deck);
                deckViewRepository.deleteByIdDeckId(deck.getId());
                deckViewRepository.flush();
                deckCardRepository.deleteByIdDeckId(deck.getId());
                deckCardRepository.flush();
                deckUserRepository.deleteByIdDeckId(deck.getId());
                deckUserRepository.flush();
                deckCardHistoryRepository.deleteByDeckId(deck.getId());
                deckCardHistoryRepository.flush();
                deckRepository.deleteById(deck.getId());
                deckRepository.flush();
            }
        }
    }

    @Scheduled(cron = "${jobs.deckCleanCron:0 0 1 * * *}")
    @Transactional
    public void aiCleanScheduler() {
        aiService.cleanupOldAsks();
    }
}
