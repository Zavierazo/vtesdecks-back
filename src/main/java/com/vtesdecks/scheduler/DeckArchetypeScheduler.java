package com.vtesdecks.scheduler;

import com.vtesdecks.cache.DeckIndex;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.jpa.entity.DeckArchetypeEntity;
import com.vtesdecks.jpa.entity.DeckEntity;
import com.vtesdecks.jpa.repositories.DeckArchetypeRepository;
import com.vtesdecks.jpa.repositories.DeckRepository;
import com.vtesdecks.messaging.MessageProducer;
import com.vtesdecks.util.CosineSimilarityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeckArchetypeScheduler {
    private static final double MIN_SIMILARITY = 0.5;
    private final DeckIndex deckIndex;
    private final DeckRepository deckRepository;
    private final DeckArchetypeRepository deckArchetypeRepository;
    private final MessageProducer messageProducer;

    @Scheduled(cron = "${jobs.deckArchetypeScheduler:0 0 1 * * *}")
    @Transactional
    public void deckArchetypeScheduler() {
        log.info("Starting Deck Archetype scheduler...");
        try {
            List<DeckArchetypeEntity> deckArchetypeList = deckArchetypeRepository.findAll();
            Map<Integer, Deck> archetypeDeckMap = getArchetypeDeckMap(deckArchetypeList);
            Map<Integer, Map<Integer, Integer>> archetypeVectorMap = getArchetypeVectorMap(deckArchetypeList, archetypeDeckMap);
            for (DeckEntity deckEntity : deckRepository.findAll()) {
                Deck deck = deckIndex.get(deckEntity.getId());
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
            Map<Integer, Deck> archetypeDeckMap = getArchetypeDeckMap(deckArchetypeList);
            Map<Integer, Map<Integer, Integer>> archetypeVectorMap = getArchetypeVectorMap(deckArchetypeList, archetypeDeckMap);
            for (DeckEntity deckEntity : deckRepository.findAll()) {
                Deck deck = deckIndex.get(deckEntity.getId());
                if (deck != null) {
                    findBestArchetypeDeck(deckEntity, deck, archetypeId, archetypeVectorMap, archetypeDeckMap);
                }
            }
            log.info("Deck Archetype for archetype {} completed successfully.", archetypeId);
        } catch (Exception e) {
            log.error("Error during Deck Archetype for archetype {}", archetypeId, e);
        }
    }

    private void findBestArchetypeDeck(DeckEntity deckEntity, Deck deck, Integer archetypeId, Map<Integer, Map<Integer, Integer>> archetypeVectorMap, Map<Integer, Deck> archetypeDeckMap) {
        Map<Integer, Integer> deckVector = CosineSimilarityUtils.getVector(deck);
        double bestSimilarity = -1.0;
        Integer bestArchetypeId = null;
        // If archetypeId is provided, check it first to potentially skip processing
        if (archetypeId != null) {
            Map<Integer, Integer> archetypeVector = archetypeVectorMap.get(archetypeId);
            if (archetypeVector != null) {
                Deck archetypeDeck = archetypeDeckMap.get(archetypeId);
                double similarity = CosineSimilarityUtils.cosineSimilarity(archetypeDeck, archetypeVector, deck, deckVector);
                if (similarity < MIN_SIMILARITY) {
                    return;
                }
            }
        }

        for (Map.Entry<Integer, Map<Integer, Integer>> archetypeVectorEntry : archetypeVectorMap.entrySet()) {
            Integer id = archetypeVectorEntry.getKey();
            Deck archetypeDeck = archetypeDeckMap.get(archetypeVectorEntry.getKey());
            Map<Integer, Integer> archetypeVector = archetypeVectorEntry.getValue();
            double similarity = CosineSimilarityUtils.cosineSimilarity(archetypeDeck, archetypeVector, deck, deckVector);
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

    private Map<Integer, Deck> getArchetypeDeckMap(List<DeckArchetypeEntity> deckArchetypeList) {
        return deckArchetypeList.stream()
                .filter(archetype -> archetype.getDeckId() != null)
                .collect(
                        Collectors.toMap(
                                DeckArchetypeEntity::getId,
                                archetype -> deckIndex.get(archetype.getDeckId())
                        )
                );
    }

    private Map<Integer, Map<Integer, Integer>> getArchetypeVectorMap(List<DeckArchetypeEntity> deckArchetypeList, Map<Integer, Deck> archetypeDeckMap) {
        return deckArchetypeList.stream()
                .filter(archetype -> deckIndex.get(archetype.getDeckId()) != null)
                .collect(
                        Collectors.toMap(
                                DeckArchetypeEntity::getId,
                                archetype -> CosineSimilarityUtils.getVector(archetypeDeckMap.get(archetype.getId()))
                        )
                );
    }

}
