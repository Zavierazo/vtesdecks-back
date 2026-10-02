package com.vtesdecks.scheduler;

import com.vtesdecks.cache.DeckCardIndex;
import com.vtesdecks.cache.indexable.DeckSummary;
import com.vtesdecks.jpa.entity.DeckArchetypeEntity;
import com.vtesdecks.jpa.entity.DeckEntity;
import com.vtesdecks.jpa.repositories.DeckArchetypeRepository;
import com.vtesdecks.jpa.repositories.DeckRepository;
import com.vtesdecks.messaging.MessageProducer;
import com.vtesdecks.model.ArchetypeCardRequirement;
import com.vtesdecks.service.DeckService;
import com.vtesdecks.util.CosineSimilarityUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
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
        List<DeckArchetypeEntity> archetypes = deckArchetypeRepository.findAll();
        Map<Integer, List<DeckSummary>> decks = getArchetypeDeckMap(archetypes);
        Map<Integer, List<Map<Integer, Integer>>> vectors = getArchetypeVectorMap(decks);
        Map<Integer, List<ArchetypeCardRequirement>> requirements = new HashMap<>();
        for (DeckArchetypeEntity archetype : archetypes) {
            requirements.put(archetype.getId(), archetype.getCardRequirements());
        }
        for (DeckEntity entity : deckRepository.findAll()) {
            DeckSummary deck = deckService.getSummary(entity.getId());
            if (deck != null) {
                findBestArchetypeDeck(entity, deck, requirements, vectors, decks);
            }
        }
    }

    private void findBestArchetypeDeck(DeckEntity deckEntity, DeckSummary deck, Map<Integer, List<ArchetypeCardRequirement>> requirements, Map<Integer, List<Map<Integer, Integer>>> archetypeVectorMap, Map<Integer, List<DeckSummary>> archetypeDeckMap) {
        Map<Integer, Integer> deckVector = deckCardIndex.getCardCounts(deck.getId());
        double bestSimilarity = -1.0;
        Integer bestArchetypeId = null;
        for (Map.Entry<Integer, List<Map<Integer, Integer>>> archetypeVectorEntry : archetypeVectorMap.entrySet()) {
            Integer id = archetypeVectorEntry.getKey();
            var rules = requirements.get(id);
            if (rules != null && rules.stream().anyMatch(rule -> deckVector.getOrDefault(rule.getCardId(), 0) < rule.getMinimumQuantity())) {
                continue;
            }
            double similarity = bestSimilarity(archetypeDeckMap.get(id), archetypeVectorEntry.getValue(), deck, deckVector);
            if (similarity >= MIN_SIMILARITY && similarity > bestSimilarity) {
                bestSimilarity = similarity;
                bestArchetypeId = id;
            }
        }
        if (bestArchetypeId != null) {
            // If a best archetype is found, assign it if different from current
            if (deckEntity.getDeckArchetypeId() == null || !deckEntity.getDeckArchetypeId().equals(bestArchetypeId)) {
                saveDeck(deckEntity, bestArchetypeId);
                log.info("Assigned deck {} to archetype {} with similarity {}", deck.getId(), bestArchetypeId, bestSimilarity);
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

    private double bestSimilarity(List<DeckSummary> archetypeDecks, List<Map<Integer, Integer>> archetypeVectors, DeckSummary deck, Map<Integer, Integer> deckVector) {
        double best = -1.0;
        for (int i = 0; i < archetypeDecks.size(); i++) {
            double similarity = CosineSimilarityUtils.cosineSimilarity(archetypeDecks.get(i), archetypeVectors.get(i), deck, deckVector);
            if (similarity > best) {
                best = similarity;
            }
        }
        return best;
    }

    private Map<Integer, List<DeckSummary>> getArchetypeDeckMap(List<DeckArchetypeEntity> deckArchetypeList) {
        Map<Integer, List<DeckSummary>> archetypeDeckMap = new HashMap<>();
        for (DeckArchetypeEntity archetype : deckArchetypeList) {
            List<DeckSummary> referenceDecks = Stream.of(archetype.getDeckId(), archetype.getSecondaryDeckId())
                    .filter(Objects::nonNull)
                    .map(deckService::getSummary)
                    .filter(Objects::nonNull)
                    .toList();
            if (!referenceDecks.isEmpty()) {
                archetypeDeckMap.put(archetype.getId(), referenceDecks);
            }
        }
        return archetypeDeckMap;
    }

    private Map<Integer, List<Map<Integer, Integer>>> getArchetypeVectorMap(Map<Integer, List<DeckSummary>> archetypeDeckMap) {
        Map<Integer, List<Map<Integer, Integer>>> archetypeVectorMap = new HashMap<>();
        for (Map.Entry<Integer, List<DeckSummary>> entry : archetypeDeckMap.entrySet()) {
            archetypeVectorMap.put(entry.getKey(), entry.getValue().stream()
                    .map(deck -> deckCardIndex.getCardCounts(deck.getId()))
                    .toList());
        }
        return archetypeVectorMap;
    }

}
