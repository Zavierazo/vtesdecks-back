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
import com.vtesdecks.model.DeckSort;
import com.vtesdecks.model.MetaType;
import com.vtesdecks.model.api.ApiDeckArchetype;
import com.vtesdecks.scheduler.DeckArchetypeScheduler;
import com.vtesdecks.util.CosineSimilarityUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class DeckArchetypeService {

    private final DeckArchetypeRepository repository;
    private final DeckArchetypeMapper mapper;
    private final DeckIndex deckIndex;
    private final DeckArchetypeIndex deckArchetypeIndex;
    private final DeckArchetypeRedisRepository redisRepository;
    private final DeckArchetypeScheduler deckArchetypeScheduler;

    public List<ApiDeckArchetype> getAll(boolean showDisabled, MetaType metaType, String currencyCode) {
        List<DeckArchetype> deckArchetypeList = StreamSupport.stream(redisRepository.findAll().spliterator(), false).toList();
        List<ApiDeckArchetype> apiDeckArchetypeList = mapper.map(deckArchetypeList, getMetaTotal(metaType), metaType, currencyCode);
        return apiDeckArchetypeList.stream()
                .filter(deck -> showDisabled || Boolean.TRUE.equals(deck.getEnabled()))
                .filter(deck -> showDisabled || deck.getMetaCount() != null && deck.getMetaCount() > 0)
                .sorted((a, b) -> {
                    // Put Unclassified archetype always at the end
                    Integer aId = a.getId();
                    Integer bId = b.getId();
                    if (aId != null && aId == 0 && (bId == null || bId != 0)) return 1;
                    if (bId != null && bId == 0 && (aId == null || aId != 0)) return -1;
                    // Otherwise sort by metaCount descending (metaCount is non-null due to previous filter)
                    return b.getMetaCount().compareTo(a.getMetaCount());
                })
                .toList();
    }

    public Optional<ApiDeckArchetype> getById(Integer id, String currencyCode) {
        return redisRepository.findById(id).map(archetype -> mapper.map(archetype, getMetaTotal(MetaType.TOURNAMENT), MetaType.TOURNAMENT, currencyCode));
    }

    public Optional<ApiDeckArchetype> getByDeckId(String deckId, String currencyCode) {
        Optional<DeckArchetype> entity = redisRepository.findByDeckId(deckId);
        return entity.map(archetype -> mapper.map(archetype, getMetaTotal(MetaType.TOURNAMENT), MetaType.TOURNAMENT, currencyCode));
    }

    public Optional<ApiDeckArchetype> create(ApiDeckArchetype api, String currencyCode) {
        DeckArchetypeEntity entity = mapper.map(api);
        DeckArchetypeEntity saved = repository.save(entity);
        deckArchetypeScheduler.updateDeckArchetype(saved.getId());
        deckArchetypeIndex.refreshIndex(saved.getId());
        deckIndex.refreshIndex(saved.getDeckId());
        return getById(saved.getId(), currencyCode);
    }

    public Optional<ApiDeckArchetype> update(Integer id, ApiDeckArchetype api, String currencyCode) {
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
        if (!Objects.equals(api.getDeckId(), saved.getDeckId())) {
            deckArchetypeScheduler.updateDeckArchetype(saved.getId());
            deckIndex.refreshIndex(api.getDeckId());
            deckIndex.refreshIndex(saved.getDeckId());
        }
        deckArchetypeIndex.refreshIndex(saved.getId());
        return getById(saved.getId(), currencyCode);
    }

    public boolean delete(Integer id) {
        Optional<DeckArchetypeEntity> deleteEntity = repository.findById(id);
        if (deleteEntity.isEmpty()) return false;
        repository.deleteById(id);
        deckIndex.refreshIndex(deleteEntity.get().getDeckId());
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


    public List<ApiDeckArchetype> getSuggestions() {
        Set<String> visitedDeckIds = new java.util.HashSet<>();
        List<ApiDeckArchetype> apiDeckArchetypes = new ArrayList<>();
        try (ResultSet<Deck> deckResultSet = deckIndex.selectAll(DeckQuery.builder()
                .type(DeckType.TOURNAMENT)
                .order(DeckSort.PLAYERS)
                .archetype(0)
                .minPlayers(20)
                .creationDate(LocalDate.now().minusDays(365))
                .build())) {
            for (Deck candidateDeck : deckResultSet) {
                if (visitedDeckIds.contains(candidateDeck.getId())) {
                    continue;
                }
                Map<Integer, Integer> candidateVector = CosineSimilarityUtils.getVector(candidateDeck);
                try (ResultSet<Deck> tournamentResultSet = deckIndex.selectAll(DeckQuery.builder()
                        .type(DeckType.TOURNAMENT)
                        .minPlayers(20)
                        .creationDate(LocalDate.now().minusDays(365))
                        .build())) {
                    List<Deck> similarTournamentDecks = tournamentResultSet.stream()
                            .filter(target -> !target.getId().equals(candidateDeck.getId()))
                            .map(target -> Pair.of(target, CosineSimilarityUtils.cosineSimilarity(candidateDeck, candidateVector, target, CosineSimilarityUtils.getVector(target))))
                            .filter(pair -> pair.getValue() >= 0.5)
                            .map(Pair::getKey)
                            .toList();
                    visitedDeckIds.addAll(similarTournamentDecks.stream().map(Deck::getId).toList());
                    if (candidateDeck.getPlayers() >= 50 || similarTournamentDecks.size() >= 3 || similarTournamentDecks.stream().anyMatch(deck -> deck.getPlayers() >= 50)) {
                        apiDeckArchetypes.add(ApiDeckArchetype.builder()
                                .name("Suggestion: " + candidateDeck.getName())
                                .description("Auto-generated suggestion based on similar decks in the last year.")
                                .deckId(candidateDeck.getId())
                                .enabled(true)
                                .metaCount((long) similarTournamentDecks.size() + 1)
                                .metaTotal(getMetaTotal(MetaType.TOURNAMENT_365))
                                .build());
                    }
                }
            }
        }
        return apiDeckArchetypes;
    }

    private long deckCount(DeckQuery query) {
        try (ResultSet<Deck> deckResultSet = deckIndex.selectAll(query)) {
            return deckResultSet.stream().count();
        }
    }
}
