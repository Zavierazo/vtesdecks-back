package com.vtesdecks.service;

import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.api.mapper.DeckArchetypeMapper;
import com.vtesdecks.cache.DeckArchetypeIndex;
import com.vtesdecks.cache.DeckIndex;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.cache.redis.entity.DeckArchetype;
import com.vtesdecks.cache.redis.repositories.DeckArchetypeRedisRepository;
import com.vtesdecks.jpa.entity.DeckArchetypeEntity;
import com.vtesdecks.jpa.repositories.DeckArchetypeRepository;
import com.vtesdecks.model.DeckQuery;
import com.vtesdecks.model.MetaType;
import com.vtesdecks.model.api.ApiDeckArchetype;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class DeckArchetypeService {

    private final DeckArchetypeRepository repository;
    private final DeckArchetypeMapper mapper;
    private final DeckIndex deckIndex;
    private final DeckArchetypeIndex deckArchetypeIndex;
    private final DeckArchetypeRedisRepository redisRepository;

    public List<ApiDeckArchetype> getAll(boolean showDisabled, MetaType metaType) {
        List<DeckArchetype> deckArchetypeList = StreamSupport.stream(redisRepository.findAll().spliterator(), false).toList();
        List<ApiDeckArchetype> apiDeckArchetypeList = mapper.map(deckArchetypeList, getMetaTotal(metaType), metaType);
        return apiDeckArchetypeList.stream()
                .filter(deck -> showDisabled || Boolean.TRUE.equals(deck.getEnabled()))
                .filter(deck -> deck.getMetaCount() != null && deck.getMetaCount() > 0)
                .sorted(Comparator.comparing(ApiDeckArchetype::getMetaCount, Comparator.reverseOrder()))
                .toList();
    }

    public Optional<ApiDeckArchetype> getById(Integer id) {
        return redisRepository.findById(id).map(archetype -> mapper.map(archetype, getMetaTotal(MetaType.TOURNAMENT), MetaType.TOURNAMENT));
    }

    public Optional<ApiDeckArchetype> getByDeckId(String deckId) {
        Optional<DeckArchetype> entity = redisRepository.findByDeckId(deckId);
        return entity.map(archetype -> mapper.map(archetype, getMetaTotal(MetaType.TOURNAMENT), MetaType.TOURNAMENT));
    }

    public ApiDeckArchetype create(ApiDeckArchetype api) {
        DeckArchetypeEntity entity = mapper.map(api);
        DeckArchetypeEntity saved = repository.save(entity);
        deckArchetypeIndex.refreshIndex(saved.getId());
        return mapper.map(saved);
    }

    public Optional<ApiDeckArchetype> update(Integer id, ApiDeckArchetype api) {
        Optional<DeckArchetypeEntity> maybe = repository.findById(id);
        if (maybe.isEmpty()) return Optional.empty();
        DeckArchetypeEntity entity = maybe.get();
        entity.setName(api.getName());
        entity.setIcon(api.getIcon());
        entity.setType(api.getType());
        entity.setDescription(api.getDescription());
        entity.setDeckId(api.getDeckId());
        entity.setEnabled(api.getEnabled());
        DeckArchetypeEntity saved = repository.save(entity);
        deckArchetypeIndex.refreshIndex(saved.getId());
        return Optional.of(mapper.map(saved));
    }

    public boolean delete(Integer id) {
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        deckArchetypeIndex.refreshIndex(id);
        return true;
    }

    private long getMetaTotal(MetaType metaType) {
        return switch (metaType) {
            case TOURNAMENT_90 ->
                    deckCount(DeckQuery.builder().type(DeckType.TOURNAMENT).creationDate(LocalDate.now().minusDays(90)).build());
            case TOURNAMENT_180 ->
                    deckCount(DeckQuery.builder().type(DeckType.TOURNAMENT).creationDate(LocalDate.now().minusDays(180)).build());
            case TOURNAMENT_365 ->
                    deckCount(DeckQuery.builder().type(DeckType.TOURNAMENT).creationDate(LocalDate.now().minusDays(365)).build());
            default -> deckCount(DeckQuery.builder().type(DeckType.TOURNAMENT).build());
        };
    }

    private long deckCount(DeckQuery query) {
        try (ResultSet<Deck> deckResultSet = deckIndex.selectAll(query)) {
            return deckResultSet.stream().count();
        }
    }
}
