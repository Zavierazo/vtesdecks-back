package com.vtesdecks.scheduler;

import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.jpa.entity.DeckArchetypeEntity;
import com.vtesdecks.jpa.entity.DeckEntity;
import com.vtesdecks.jpa.repositories.DeckArchetypeRepository;
import com.vtesdecks.jpa.repositories.DeckRepository;
import com.vtesdecks.messaging.MessageProducer;
import com.vtesdecks.service.DeckService;
import com.vtesdecks.util.CosineSimilarityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeckArchetypeScheduler {
    private static final double MIN_SIMILARITY = 0.5;
    private final DeckService deckService;
    private final DeckRepository deckRepository;
    private final DeckArchetypeRepository deckArchetypeRepository;
    private final MessageProducer messageProducer;

    @Scheduled(cron = "${jobs.deckArchetypeScheduler:0 30 * * * *}")
    @Transactional
    public void deckArchetypeScheduler() {
        log.info("Starting Deck Archetype scheduler...");
        try {
            List<DeckArchetypeEntity> deckArchetypeList = deckArchetypeRepository.findAll();
            Map<Integer, List<Deck>> archetypeDeckMap = getArchetypeDeckMap(deckArchetypeList);
            Map<Integer, List<Map<Integer, Integer>>> archetypeVectorMap = getArchetypeVectorMap(archetypeDeckMap);
            for (DeckEntity deckEntity : deckRepository.findAll()) {
                Deck deck = deckService.getDeck(deckEntity.getId());
                if (deck != null) {
                    findBestArchetypeDeck(deckEntity, deck, null, archetypeVectorMap, archetypeDeckMap);
                }
            }
            log.info("Deck Archetype scheduler completed successfully.");
        } catch (Exception e) {
            log.error("Error during Deck Archetype scheduler", e);
        }
    }

    public void updateDeckArchetype(Integer archetypeId) {
        log.info("Starting Deck Archetype for archetypeId {}...", archetypeId);
        try {
            List<DeckArchetypeEntity> deckArchetypeList = deckArchetypeRepository.findAll();
            Map<Integer, List<Deck>> archetypeDeckMap = getArchetypeDeckMap(deckArchetypeList);
            Map<Integer, List<Map<Integer, Integer>>> archetypeVectorMap = getArchetypeVectorMap(archetypeDeckMap);
            for (DeckEntity deckEntity : deckRepository.findAll()) {
                Deck deck = deckService.getDeck(deckEntity.getId());
                if (deck != null) {
                    findBestArchetypeDeck(deckEntity, deck, archetypeId, archetypeVectorMap, archetypeDeckMap);
                }
            }
            log.info("Deck Archetype for archetype {} completed successfully.", archetypeId);
        } catch (Exception e) {
            log.error("Error during Deck Archetype for archetype {}", archetypeId, e);
        }
    }

    private void findBestArchetypeDeck(DeckEntity deckEntity, Deck deck, Integer archetypeId, Map<Integer, List<Map<Integer, Integer>>> archetypeVectorMap, Map<Integer, List<Deck>> archetypeDeckMap) {
        Map<Integer, Integer> deckVector = CosineSimilarityUtils.getVector(deck);
        double bestSimilarity = -1.0;
        Integer bestArchetypeId = null;
        // If archetypeId is provided, check it first to potentially skip processing
        if (archetypeId != null) {
            List<Map<Integer, Integer>> archetypeVectors = archetypeVectorMap.get(archetypeId);
            if (archetypeVectors != null) {
                double similarity = bestSimilarity(archetypeDeckMap.get(archetypeId), archetypeVectors, deck, deckVector);
                if (similarity < MIN_SIMILARITY) {
                    return;
                }
            }
        }

        for (Map.Entry<Integer, List<Map<Integer, Integer>>> archetypeVectorEntry : archetypeVectorMap.entrySet()) {
            Integer id = archetypeVectorEntry.getKey();
            double similarity = bestSimilarity(archetypeDeckMap.get(id), archetypeVectorEntry.getValue(), deck, deckVector);
            if (similarity >= MIN_SIMILARITY && similarity > bestSimilarity) {
                bestSimilarity = similarity;
                bestArchetypeId = id;
            }
        }
        if (bestArchetypeId != null) {
            // If a best archetype is found, assign it if different from current
            if (deck.getDeckArchetypeId() == null || !deck.getDeckArchetypeId().equals(bestArchetypeId)) {
                saveDeck(deckEntity, bestArchetypeId);
                log.info("Assigned deck {} to archetype {} with similarity {}", deck.getId(), bestArchetypeId, bestSimilarity);
            }
        } else {
            // If no archetype matched, remove existing archetype assignment
            if (deck.getDeckArchetypeId() != null) {
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

    private double bestSimilarity(List<Deck> archetypeDecks, List<Map<Integer, Integer>> archetypeVectors, Deck deck, Map<Integer, Integer> deckVector) {
        double best = -1.0;
        for (int i = 0; i < archetypeDecks.size(); i++) {
            double similarity = CosineSimilarityUtils.cosineSimilarity(archetypeDecks.get(i), archetypeVectors.get(i), deck, deckVector);
            if (similarity > best) {
                best = similarity;
            }
        }
        return best;
    }

    private Map<Integer, List<Deck>> getArchetypeDeckMap(List<DeckArchetypeEntity> deckArchetypeList) {
        Map<Integer, List<Deck>> archetypeDeckMap = new HashMap<>();
        for (DeckArchetypeEntity archetype : deckArchetypeList) {
            List<Deck> referenceDecks = Stream.of(archetype.getDeckId(), archetype.getSecondaryDeckId())
                    .filter(Objects::nonNull)
                    .map(deckService::getDeck)
                    .filter(Objects::nonNull)
                    .toList();
            if (!referenceDecks.isEmpty()) {
                archetypeDeckMap.put(archetype.getId(), referenceDecks);
            }
        }
        return archetypeDeckMap;
    }

    private Map<Integer, List<Map<Integer, Integer>>> getArchetypeVectorMap(Map<Integer, List<Deck>> archetypeDeckMap) {
        Map<Integer, List<Map<Integer, Integer>>> archetypeVectorMap = new HashMap<>();
        for (Map.Entry<Integer, List<Deck>> entry : archetypeDeckMap.entrySet()) {
            archetypeVectorMap.put(entry.getKey(), entry.getValue().stream()
                    .map(CosineSimilarityUtils::getVector)
                    .toList());
        }
        return archetypeVectorMap;
    }

}
