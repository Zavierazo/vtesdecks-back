package com.vtesdecks.cache;

import com.vtesdecks.cache.factory.DeckArchetypeFactory;
import com.vtesdecks.cache.redis.entity.DeckArchetype;
import com.vtesdecks.cache.redis.repositories.DeckArchetypeRedisRepository;
import com.vtesdecks.jpa.entity.DeckArchetypeEntity;
import com.vtesdecks.jpa.repositories.DeckArchetypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class DeckArchetypeIndex {

    private final DeckArchetypeRedisRepository deckArchetypeRedisRepository;
    private final DeckArchetypeRepository deckArchetypeRepository;
    private final DeckArchetypeFactory deckArchetypeFactory;

    @Scheduled(cron = "${jobs.cache.deck-archetype.refresh:0 0 * * * *}")
    public void refreshIndex() {
        StopWatch stopWatch = new StopWatch();
        try {
            stopWatch.start();
            Set<Integer> currentKeys = new HashSet<>();
            for (DeckArchetype deckArchetype : deckArchetypeRedisRepository.findAll()) {
                currentKeys.add(deckArchetype.getId());
            }
            for (DeckArchetypeEntity deckArchetypeEntity : deckArchetypeRepository.findAll()) {
                DeckArchetype deckArchetype = deckArchetypeFactory.getDeckArchetype(deckArchetypeEntity);
                deckArchetypeRedisRepository.save(deckArchetype);
                currentKeys.remove(deckArchetype.getId());
            }
            if (!currentKeys.isEmpty()) {
                log.warn("Deleting form index deck archetypes {}", currentKeys);
                deckArchetypeRedisRepository.deleteAllById(currentKeys);
            }
        } finally {
            stopWatch.stop();
            log.info("Index finished in {} ms. Colletion size is {}", stopWatch.lastTaskInfo().getTimeMillis(), deckArchetypeRedisRepository.count());
        }
    }

    public void refreshIndex(Integer id) {
        deckArchetypeRepository.findById(id).ifPresentOrElse(
                entity -> deckArchetypeRedisRepository.save(deckArchetypeFactory.getDeckArchetype(entity)),
                () -> deckArchetypeRedisRepository.deleteById(id)
        );
    }
}
