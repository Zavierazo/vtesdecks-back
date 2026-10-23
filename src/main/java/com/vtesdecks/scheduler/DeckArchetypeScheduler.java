package com.vtesdecks.scheduler;

import com.vtesdecks.cache.DeckCardIndex;
import com.vtesdecks.cache.indexable.DeckSummary;
import com.vtesdecks.jpa.entity.DeckEntity;
import com.vtesdecks.jpa.repositories.DeckRepository;
import com.vtesdecks.messaging.MessageProducer;
import com.vtesdecks.service.DeckArchetypeMatcher;
import com.vtesdecks.service.DeckService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeckArchetypeScheduler {
    private final DeckCardIndex deckCardIndex;
    private final DeckArchetypeMatcher archetypeMatcher;
    private final DeckService deckService;
    private final DeckRepository deckRepository;
    private final MessageProducer messageProducer;

    @Scheduled(cron = "${jobs.deckArchetypeScheduler:0 30 * * * *}")
    @Transactional
    public void deckArchetypeScheduler() {
        log.info("Starting Deck Archetype scheduler...");
        try {
            reclassifyDecks();
            log.info("Deck Archetype scheduler completed successfully.");
        } catch (Exception e) {
            log.error("Error during Deck Archetype scheduler", e);
        }
    }

    public void updateDeckArchetype(Integer archetypeId) {
        log.info("Reclassifying decks after updating archetype {}", archetypeId);
        reclassifyDecks();
    }

    private void reclassifyDecks() {
        List<DeckArchetypeMatcher.Reference> references = archetypeMatcher.loadReferences();
        for (DeckEntity entity : deckRepository.findAll()) {
            DeckSummary deck = deckService.getSummary(entity.getId());
            if (deck != null) {
                findBestArchetypeDeck(entity, deck, references);
            }
        }
    }

    private void findBestArchetypeDeck(DeckEntity deckEntity, DeckSummary deck, List<DeckArchetypeMatcher.Reference> references) {
        Map<Integer, Integer> deckVector = deckCardIndex.getCardCounts(deck.getId());
        var match = archetypeMatcher.findBestMatch(deck, deckVector, references);
        Integer bestArchetypeId = match == null ? null : match.getId();
        if (bestArchetypeId != null) {
            // If a best archetype is found, assign it if different from current
            if (deckEntity.getDeckArchetypeId() == null || !deckEntity.getDeckArchetypeId().equals(bestArchetypeId)) {
                saveDeck(deckEntity, bestArchetypeId);
                log.info("Assigned deck {} to archetype {}", deck.getId(), bestArchetypeId);
            }
        } else {
            // If no archetype matched, remove existing archetype assignment
            if (deckEntity.getDeckArchetypeId() != null) {
                saveDeck(deckEntity, null);
                log.info("Removed archetype assignment from deck {}", deck.getId());
            }
        }
    }

    private void saveDeck(DeckEntity deckEntity, Integer deckArchetypeId) {
        deckEntity.setDeckArchetypeId(deckArchetypeId);
        deckRepository.saveAndFlush(deckEntity);
        deckRepository.flush();
        messageProducer.publishDeckSync(deckEntity.getId());
    }

}
