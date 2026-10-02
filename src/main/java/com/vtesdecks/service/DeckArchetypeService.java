package com.vtesdecks.service;

import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.api.mapper.DeckArchetypeMapper;
import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.DeckArchetypeIndex;
import com.vtesdecks.cache.DeckCardIndex;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.indexable.DeckSummary;
import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.cache.redis.entity.DeckArchetype;
import com.vtesdecks.cache.redis.repositories.DeckArchetypeRedisRepository;
import com.vtesdecks.jpa.entity.DeckArchetypeEntity;
import com.vtesdecks.jpa.repositories.DeckArchetypeRepository;
import com.vtesdecks.messaging.MessageProducer;
import com.vtesdecks.model.ArchetypeCardRequirement;
import com.vtesdecks.model.ArchetypeMetaMetrics;
import com.vtesdecks.model.DeckQuery;
import com.vtesdecks.model.DeckSort;
import com.vtesdecks.model.MetaType;
import com.vtesdecks.model.api.ApiDeckArchetype;
import com.vtesdecks.model.api.ApiNearestArchetype;
import com.vtesdecks.model.api.ApiSearchArchetype;
import com.vtesdecks.scheduler.DeckArchetypeScheduler;
import com.vtesdecks.util.CosineSimilarityUtils;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class DeckArchetypeService {
    private final DeckCardIndex deckCardIndex;


    private final DeckArchetypeRepository repository;
    private final DeckArchetypeMapper mapper;
    private final DeckService deckService;
    private final DeckArchetypeIndex deckArchetypeIndex;
    private final DeckArchetypeRedisRepository redisRepository;
    private final DeckArchetypeScheduler deckArchetypeScheduler;
    private final MessageProducer messageProducer;
    private final CryptCache cryptCache;
    private final LibraryCache libraryCache;

    private void validateRequirements(List<ArchetypeCardRequirement> requirements) {
        if (requirements == null) {
            return;
        }
        Set<Integer> ids = new HashSet<>();
        for (var requirement : requirements) {
            if (requirement == null || requirement.getCardId() == null
                    || requirement.getMinimumQuantity() == null || requirement.getMinimumQuantity() < 1
                    || !ids.add(requirement.getCardId())
                    || (cryptCache.get(requirement.getCardId()) == null && libraryCache.get(requirement.getCardId()) == null)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Each requirement needs a unique valid card and a positive integer minimum quantity");
            }
        }
    }


    public List<ApiDeckArchetype> getAll(boolean showDisabled, MetaType metaType, String currencyCode) {
        List<DeckArchetype> deckArchetypeList = StreamSupport.stream(redisRepository.findAll().spliterator(), false).toList();
        List<ApiDeckArchetype> apiDeckArchetypeList = mapper.map(deckArchetypeList, getMetaMetrics(metaType), metaType, currencyCode);
        return apiDeckArchetypeList.stream()
                .filter(deck -> showDisabled || Boolean.TRUE.equals(deck.getEnabled()))
                .filter(deck -> deck.getMetaCount() != null && deck.getMetaCount() > 0)
                .sorted((a, b) -> {
                    // Put Unclassified archetype always at the end
                    Integer aId = a.getId();
                    Integer bId = b.getId();
                    if (aId != null && aId == 0 && (bId == null || bId != 0)) return 1;
                    if (bId != null && bId == 0 && (aId == null || aId != 0)) return -1;
                    // Otherwise sort by metaCount descending (metaCount is non-null due to previous filter)
                    int comparison = b.getMetaCount().compareTo(a.getMetaCount());
                    if (comparison != 0) return comparison;
                    return b.getDeckCount().compareTo(a.getDeckCount());
                })
                .toList();
    }

    public List<ApiSearchArchetype> searchByName(String query, int limit) {
        if (query == null || query.isBlank() || limit <= 0) {
            return List.of();
        }

        String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
        Comparator<DeckArchetype> relevance = Comparator
                .comparingInt((DeckArchetype archetype) -> matchRank(archetype.getName(), normalizedQuery))
                .thenComparing(archetype -> archetype.getName().toLowerCase(Locale.ROOT))
                .thenComparing(DeckArchetype::getName);

        return StreamSupport.stream(redisRepository.findAll().spliterator(), false)
                .filter(archetype -> archetype.getId() != null && archetype.getId() > 0)
                .filter(archetype -> Boolean.TRUE.equals(archetype.getEnabled()))
                .filter(archetype -> archetype.getName() != null && !archetype.getName().isBlank())
                .filter(archetype -> archetype.getName().toLowerCase(Locale.ROOT).contains(normalizedQuery))
                .sorted(relevance)
                .limit(limit)
                .map(archetype -> ApiSearchArchetype.builder()
                        .id(archetype.getId())
                        .name(archetype.getName())
                        .icon(archetype.getIcon())
                        .type(archetype.getType())
                        .build())
                .toList();
    }

    private int matchRank(String name, String normalizedQuery) {
        String normalizedName = name.toLowerCase(Locale.ROOT);
        if (normalizedName.equals(normalizedQuery)) {
            return 0;
        }
        return normalizedName.startsWith(normalizedQuery) ? 1 : 2;
    }

    public Optional<ApiDeckArchetype> getById(Integer id, MetaType metaType, String currencyCode) {
        return redisRepository.findById(id).map(archetype -> {
            ApiDeckArchetype api = mapper.map(archetype, getMetaMetrics(metaType), metaType, currencyCode);
            api.setKeyCrypt(mapper.mapKeyCrypt(archetype.getKeyCards()));
            api.setKeyLibrary(mapper.mapKeyLibrary(archetype.getKeyCards()));
            return api;
        });
    }

    public Optional<ApiDeckArchetype> getByDeckId(String deckId, String currencyCode) {
        Optional<DeckArchetype> entity = redisRepository.findByDeckId(deckId);
        return entity.map(archetype -> {
            ApiDeckArchetype api = mapper.map(archetype, getMetaMetrics(MetaType.TOURNAMENT), MetaType.TOURNAMENT, currencyCode);
            api.setKeyCrypt(mapper.mapKeyCrypt(archetype.getKeyCards()));
            api.setKeyLibrary(mapper.mapKeyLibrary(archetype.getKeyCards()));
            return api;
        });
    }

    @Transactional
    public Optional<ApiDeckArchetype> create(ApiDeckArchetype api, String currencyCode) {
        validateRequirements(api.getCardRequirements());
        DeckArchetypeEntity entity = mapper.map(api);
        if (entity.getCardRequirements() == null) {
            entity.setCardRequirements(List.of());
        }
        DeckArchetypeEntity saved = repository.save(entity);
        deckArchetypeScheduler.updateDeckArchetype(saved.getId());
        deckArchetypeIndex.refreshIndex(saved.getId());
        publishDeckSync(saved.getDeckId());
        publishDeckSync(saved.getSecondaryDeckId());
        return getById(saved.getId(), MetaType.TOURNAMENT, currencyCode);
    }

    @Transactional
    public Optional<ApiDeckArchetype> update(Integer id, ApiDeckArchetype api, String currencyCode) {
        Optional<DeckArchetypeEntity> maybe = repository.findById(id);
        if (maybe.isEmpty()) {
            return Optional.empty();
        }
        DeckArchetypeEntity entity = maybe.get();
        validateRequirements(api.getCardRequirements());
        boolean requirementsChanged = api.getCardRequirements() != null
                && !new HashSet<>(api.getCardRequirements()).equals(new HashSet<>(entity.getCardRequirements() == null ? List.of() : entity.getCardRequirements()));
        if (api.getCardRequirements() != null) {
            entity.setCardRequirements(List.copyOf(api.getCardRequirements()));
        }
        String previousDeckId = entity.getDeckId();
        String previousSecondaryDeckId = entity.getSecondaryDeckId();
        entity.setName(api.getName());
        entity.setIcon(api.getIcon());
        entity.setType(api.getType());
        entity.setDescription(api.getDescription());
        entity.setDeckId(api.getDeckId());
        entity.setSecondaryDeckId(api.getSecondaryDeckId());
        entity.setEnabled(api.getEnabled());
        DeckArchetypeEntity saved = repository.save(entity);
        if (requirementsChanged || !Objects.equals(previousDeckId, saved.getDeckId()) || !Objects.equals(previousSecondaryDeckId, saved.getSecondaryDeckId())) {
            deckArchetypeScheduler.updateDeckArchetype(saved.getId());
            publishDeckSync(previousDeckId);
            publishDeckSync(previousSecondaryDeckId);
            publishDeckSync(saved.getDeckId());
            publishDeckSync(saved.getSecondaryDeckId());
        }
        deckArchetypeIndex.refreshIndex(saved.getId());
        return getById(saved.getId(), MetaType.TOURNAMENT, currencyCode);
    }

    public boolean delete(Integer id) {
        Optional<DeckArchetypeEntity> deleteEntity = repository.findById(id);
        if (deleteEntity.isEmpty()) return false;
        repository.deleteById(id);
        publishDeckSync(deleteEntity.get().getDeckId());
        publishDeckSync(deleteEntity.get().getSecondaryDeckId());
        deckArchetypeIndex.refreshIndex(id);
        return true;
    }

    private void publishDeckSync(String deckId) {
        if (deckId != null) {
            messageProducer.publishDeckSync(deckId);
        }
    }

    private long getMetaTotal(MetaType metaType) {
        return switch (metaType) {
            case TOURNAMENT_90 ->
                    deckCount(DeckQuery.builder().type(DeckType.TOURNAMENT).creationDate(LocalDate.now().minusDays(90)).build());
            case TOURNAMENT_180 ->
                    deckCount(DeckQuery.builder().type(DeckType.TOURNAMENT).creationDate(LocalDate.now().minusDays(180)).build());
            case TOURNAMENT_365 ->
                    deckCount(DeckQuery.builder().type(DeckType.TOURNAMENT).creationDate(LocalDate.now().minusDays(365)).build());
            case TOURNAMENT_730 ->
                    deckCount(DeckQuery.builder().type(DeckType.TOURNAMENT).creationDate(LocalDate.now().minusDays(730)).build());
            default -> deckCount(DeckQuery.builder().type(DeckType.TOURNAMENT).build());
        };
    }

    private Map<Integer, ArchetypeMetaMetrics> getMetaMetrics(MetaType metaType) {
        Map<Integer, Long> currentCounts = new HashMap<>();
        Map<Integer, Long> previousCounts = new HashMap<>();
        long currentTotal = 0;
        long previousTotal = 0;
        Integer days = switch (metaType) {
            case TOURNAMENT_90 -> 90;
            case TOURNAMENT_180 -> 180;
            case TOURNAMENT_365 -> 365;
            case TOURNAMENT_730 -> 730;
            default -> null;
        };
        LocalDateTime currentStart = days == null ? null : LocalDate.now().minusDays(days).atStartOfDay();
        LocalDateTime previousStart = days == null ? null : LocalDate.now().minusDays(days * 2L).atStartOfDay();

        try (ResultSet<DeckSummary> decks = deckService.getDecks(DeckQuery.builder().type(DeckType.TOURNAMENT).build())) {
            for (DeckSummary deck : decks) {
                Integer archetypeId = deck.getDeckArchetypeId() == null ? 0 : deck.getDeckArchetypeId();
                LocalDateTime created = deck.getCreationDate();
                if (days == null || (created != null && !created.isBefore(currentStart))) {
                    currentCounts.merge(archetypeId, 1L, Long::sum);
                    currentTotal++;
                } else if (created != null && !created.isBefore(previousStart)) {
                    previousCounts.merge(archetypeId, 1L, Long::sum);
                    previousTotal++;
                }
            }
        }

        Map<Integer, ArchetypeMetaMetrics> metrics = new HashMap<>();
        for (DeckArchetype archetype : redisRepository.findAll()) {
            metrics.put(archetype.getId(), new ArchetypeMetaMetrics(
                    currentCounts.getOrDefault(archetype.getId(), 0L),
                    currentTotal,
                    days == null ? null : previousCounts.getOrDefault(archetype.getId(), 0L),
                    days == null ? null : previousTotal));
        }
        return metrics;
    }


    public List<ApiDeckArchetype> getSuggestions() {
        List<ArchetypeReference> references = getArchetypeReferences();
        Set<String> visitedDeckIds = new HashSet<>();
        List<ApiDeckArchetype> apiDeckArchetypes = new ArrayList<>();
        try (ResultSet<DeckSummary> deckResultSet = deckService.getDecks(DeckQuery.builder()
                .type(DeckType.TOURNAMENT)
                .order(DeckSort.PLAYERS)
                .archetype(0)
                .minPlayers(20)
                .creationDate(LocalDate.now().minusYears(3))
                .build())) {
            for (DeckSummary candidateDeck : deckResultSet) {
                if (visitedDeckIds.contains(candidateDeck.getId())) {
                    continue;
                }
                Map<Integer, Integer> candidateVector = deckCardIndex.getCardCounts(candidateDeck.getId());
                try (ResultSet<DeckSummary> tournamentResultSet = deckService.getDecks(DeckQuery.builder()
                        .type(DeckType.TOURNAMENT)
                        .minPlayers(10)
                        .creationDate(LocalDate.now().minusYears(3))
                        .build())) {
                    List<DeckSummary> similarTournamentDecks = tournamentResultSet.stream()
                            .map(target -> Pair.of(target, CosineSimilarityUtils.cosineSimilarity(candidateDeck, candidateVector, target, deckCardIndex.getCardCounts(target.getId()))))
                            .filter(pair -> pair.getValue() > 0.5)
                            .map(Pair::getKey)
                            .toList();
                    visitedDeckIds.addAll(similarTournamentDecks.stream().map(DeckSummary::getId).toList());
                    if (similarTournamentDecks.stream().filter(deck -> deck.getDeckArchetypeId() == null).count() >= 2
                            && similarTournamentDecks.stream().filter(deck -> deck.getPlayers() >= 20).count() >= 2
                            && (similarTournamentDecks.size() >= 4 || similarTournamentDecks.stream().anyMatch(deck -> deck.getPlayers() >= 50))) {
                        apiDeckArchetypes.add(ApiDeckArchetype.builder()
                                .name("Suggestion: " + candidateDeck.getName())
                                .description("Auto-generated suggestion based on similar decks in the last year.")
                                .deckId(candidateDeck.getId())
                                .nearestArchetype(findNearestArchetype(candidateDeck, candidateVector, references))
                                .enabled(true)
                                .metaCount((long) similarTournamentDecks.size())
                                .metaTotal(getMetaTotal(MetaType.TOURNAMENT))
                                .build());
                    }
                }
            }
        }
        return apiDeckArchetypes;
    }

    private record ArchetypeReference(Integer id, String name, DeckSummary deck, Map<Integer, Integer> vector) {
    }

    private List<ArchetypeReference> getArchetypeReferences() {
        List<ArchetypeReference> references = new ArrayList<>();
        // Use the same archetype pool and reference decks as classification, including disabled archetypes.
        for (DeckArchetypeEntity archetype : repository.findAll().stream()
                .filter(archetype -> archetype.getId() != null && archetype.getId() > 0)
                .sorted(Comparator.comparing(DeckArchetypeEntity::getId)).toList()) {
            for (String deckId : java.util.stream.Stream.of(archetype.getDeckId(), archetype.getSecondaryDeckId())
                    .filter(Objects::nonNull).distinct().toList()) {
                DeckSummary deck = deckService.getSummary(deckId);
                if (deck != null) {
                    references.add(new ArchetypeReference(archetype.getId(), archetype.getName(), deck,
                            deckCardIndex.getCardCounts(deckId)));
                }
            }
        }
        return references;
    }

    private ApiNearestArchetype findNearestArchetype(DeckSummary candidate, Map<Integer, Integer> vector,
                                                    List<ArchetypeReference> references) {
        ApiNearestArchetype nearest = null;
        for (ArchetypeReference reference : references) {
            double similarity = CosineSimilarityUtils.cosineSimilarity(candidate, vector, reference.deck(), reference.vector());
            if (nearest == null || similarity > nearest.similarity()) {
                nearest = new ApiNearestArchetype(reference.id(), reference.name(), similarity);
            }
        }
        return nearest;
    }

    private long deckCount(DeckQuery query) {
        try (ResultSet<DeckSummary> deckResultSet = deckService.getDecks(query)) {
            return deckResultSet.stream().count();
        }
    }
}
