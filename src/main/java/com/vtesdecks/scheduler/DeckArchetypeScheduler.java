package com.vtesdecks.scheduler;

import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.cache.DeckIndex;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.jpa.entity.DeckArchetypeEntity;
import com.vtesdecks.jpa.entity.DeckEntity;
import com.vtesdecks.jpa.repositories.DeckArchetypeRepository;
import com.vtesdecks.jpa.repositories.DeckRepository;
import com.vtesdecks.messaging.MessageProducer;
import com.vtesdecks.model.DeckQuery;
import com.vtesdecks.model.DeckSort;
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
    private final DeckIndex deckIndex;
    private final DeckRepository deckRepository;
    private final DeckArchetypeRepository deckArchetypeRepository;
    private final MessageProducer messageProducer;

    @Scheduled(cron = "${jobs.deckArchetypeScheduler:0 0 1 * * *}")
    @Transactional
    public void deckArchetypeScheduler() {
        List<DeckArchetypeEntity> deckArchetypeList = deckArchetypeRepository.findAll();
        Map<Integer, Deck> archetypeDeckMap = getArchetypeDeckMap(deckArchetypeList);
        Map<Integer, Map<Integer, Integer>> archetypeVectorMap = getArchetypeVectorMap(deckArchetypeList, archetypeDeckMap);
        try (ResultSet<Deck> deckResultSet = deckIndex.selectAll(DeckQuery.builder().order(DeckSort.NEWEST).build())) {
            for (Deck deck : deckResultSet) {
                findBestArchetypeDeck(deck, archetypeVectorMap, archetypeDeckMap);
            }
        }
    }

    private void findBestArchetypeDeck(Deck deck, Map<Integer, Map<Integer, Integer>> archetypeVectorMap, Map<Integer, Deck> archetypeDeckMap) {
        Map<Integer, Integer> deckVector = CosineSimilarityUtils.getVector(deck);
        double bestSimilarity = -1.0;
        Integer bestArchetypeId = null;
        for (Map.Entry<Integer, Map<Integer, Integer>> archetypeVectorEntry : archetypeVectorMap.entrySet()) {
            Integer archetypeId = archetypeVectorEntry.getKey();
            Deck archetypeDeck = archetypeDeckMap.get(archetypeVectorEntry.getKey());
            Map<Integer, Integer> archetypeVector = archetypeVectorEntry.getValue();
            double similarity = CosineSimilarityUtils.cosineSimilarity(archetypeDeck, archetypeVector, deck, deckVector);
            if (similarity >= 0.5 && similarity > bestSimilarity) {
                bestSimilarity = similarity;
                bestArchetypeId = archetypeId;
            }
        }
        if (bestArchetypeId != null) {
            // If a best archetype is found, assign it if different from current
            if (deck.getDeckArchetypeId() == null || !deck.getDeckArchetypeId().equals(bestArchetypeId)) {
                saveDeck(deck, bestArchetypeId);
                log.info("Assigned deck {} to archetype {} with similarity {}", deck.getId(), bestArchetypeId, bestSimilarity);
            }
        } else {
            // If no archetype matched, remove existing archetype assignment
            if (deck.getDeckArchetypeId() != null) {
                saveDeck(deck, null);
                log.info("Removed archetype assignment from deck {}", deck.getId());
            }
        }
    }

    private void saveDeck(Deck deck, Integer deckArchetypeId) {
        DeckEntity deckEntity = deckRepository.findById(deck.getId()).orElse(null);
        if (deckEntity != null) {
            deckEntity.setDeckArchetypeId(deckArchetypeId);
            deckRepository.saveAndFlush(deckEntity);
            deckRepository.flush();
            messageProducer.publishDeckSync(deckEntity.getId());
        }
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
