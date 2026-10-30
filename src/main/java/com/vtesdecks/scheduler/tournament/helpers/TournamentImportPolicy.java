package com.vtesdecks.scheduler.tournament.helpers;

import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.jpa.entity.DeckCardEntity;
import com.vtesdecks.jpa.entity.DeckEntity;
import com.vtesdecks.jpa.entity.TournamentSchedulerOwner;
import com.vtesdecks.jpa.repositories.DeckCardRepository;
import com.vtesdecks.jpa.repositories.DeckRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Resolves identity and ownership before building or persisting an import.
 */
@Slf4j
@RequiredArgsConstructor
public class TournamentImportPolicy {
    private static final Pattern NAME_SEPARATORS = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final ReentrantLock[] IMPORT_LOCKS = IntStream.range(0, 256)
            .mapToObj(index -> new ReentrantLock()).toArray(ReentrantLock[]::new);
    private final DeckRepository deckRepository;
    private final DeckCardRepository deckCardRepository;
    private final TransactionTemplate transactionTemplate;

    public enum Action {
        IMPORT, ENRICH_METADATA, REPORT_VERIFIED
    }

    public void execute(String id, String eventId, Integer position, TournamentSchedulerOwner incoming,
                        BiConsumer<DeckEntity, Action> importer) {
        execute(id, eventId, position, incoming, null, importer);
    }

    public void execute(String id, String eventId, Integer position, TournamentSchedulerOwner incoming,
                        LocalDate date, BiConsumer<DeckEntity, Action> importer) {
        // Acquire before starting the transaction so waiting imports see committed results.
        int[] locks = Stream.of("id:" + id.toLowerCase(Locale.ROOT),
                        StringUtils.isNotBlank(eventId) ? "event:" + eventId.trim().toLowerCase(Locale.ROOT) : null,
                        date != null ? "date:" + date : null)
                .filter(Objects::nonNull).mapToInt(key -> Math.floorMod(key.hashCode(), IMPORT_LOCKS.length))
                .distinct().sorted().toArray();
        for (int lock : locks) {
            IMPORT_LOCKS[lock].lock();
        }
        try {
            executeInTransaction(id, eventId, position, incoming, importer);
        } finally {
            for (int index = locks.length - 1; index >= 0; index--) {
                IMPORT_LOCKS[locks[index]].unlock();
            }
        }
    }

    private void executeInTransaction(String id, String eventId, Integer position, TournamentSchedulerOwner incoming,
                                      BiConsumer<DeckEntity, Action> importer) {
        transactionTemplate.executeWithoutResult(status -> {
            Map<String, DeckEntity> matches = new LinkedHashMap<>();
            deckRepository.findById(id).ifPresent(deck -> matches.put(deck.getId(), deck));
            if (StringUtils.isNotBlank(eventId) && position != null) {
                deckRepository.findByTypeAndEventIdAndPositionAndDeletedFalse(DeckType.TOURNAMENT, eventId, position)
                        .forEach(deck -> matches.put(deck.getId(), deck));
            }
            if (matches.size() > 1) {
                log.debug("Skipping {} import {}, conflicting tournament deck identities: {}", incoming, id, matches.keySet());
                return;
            }
            DeckEntity actual = matches.values().stream().findFirst().orElse(null);
            if (actual != null) {
                if (actual.getType() != DeckType.TOURNAMENT || Boolean.TRUE.equals(actual.getDeleted())
                        || (StringUtils.isNotBlank(eventId) && StringUtils.isNotBlank(actual.getEventId())
                        && !Objects.equals(eventId, actual.getEventId()))
                        || (position != null && actual.getPosition() != null && !Objects.equals(position, actual.getPosition()))) {
                    log.debug("Skipping {} import {}, existing deck {} has an incompatible identity or is deleted", incoming, id, actual.getId());
                    return;
                }
            }
            Action action = actionFor(actual, incoming);
            if (action == null) {
                log.debug("Skipping {} import {}, existing owner is {}", incoming, id, actual.getSchedulerOwner());
                return;
            }
            importer.accept(actual, action);
        });
    }

    public boolean canPersist(DeckEntity actual, DeckEntity deck) {
        if (actual != null || StringUtils.isNoneBlank(deck.getEventId(), deck.getVeknId())) {
            return true;
        }
        log.warn("Skipping new {} tournament deck {}: eventId and veknId are required."
                        + " eventId={}, veknId={}, tournament={}, date={}, source={}",
                deck.getSchedulerOwner(), deck.getId(), deck.getEventId(), deck.getVeknId(),
                deck.getTournament(), deck.getCreationDate(), deck.getSource());
        return false;
    }

    public boolean hasImportConflict(DeckEntity actual, DeckEntity deck, Collection<DeckCardEntity> cards) {
        return !findDuplicates(actual, deck, cards).isEmpty();
    }

    public List<DeckEntity> findDuplicates(DeckEntity actual, DeckEntity deck, Collection<DeckCardEntity> cards) {
        if (StringUtils.isNotBlank(deck.getEventId()) && deck.getPosition() != null) {
            List<DeckEntity> duplicates = deckRepository.findByTypeAndEventIdAndPositionAndDeletedFalse(
                            DeckType.TOURNAMENT, deck.getEventId(), deck.getPosition()).stream()
                    .filter(existing -> !Objects.equals(existing.getId(), deck.getId())).toList();
            if (!duplicates.isEmpty()) {
                log.debug("Skipping deck {}, event {} already has decks {} in position {}", deck.getId(), deck.getEventId(),
                        duplicates.stream().map(DeckEntity::getId).toList(), deck.getPosition());
                return duplicates;
            }
        }
        return actual == null ? findMatchingDecks(deck, cards) : List.of();
    }

    public boolean canEnrichDuplicate(DeckEntity existing, DeckEntity incoming) {
        return StringUtils.isNotBlank(incoming.getEventId())
                && incoming.getEventId().equals(existing.getEventId())
                && Objects.equals(Objects.requireNonNullElse(existing.getPosition(), 1),
                Objects.requireNonNullElse(incoming.getPosition(), 1));
    }

    /**
     * Detects the same result under another source ID without conflating different finalists.
     */
    private List<DeckEntity> findMatchingDecks(DeckEntity deck, Collection<DeckCardEntity> cards) {
        String tournament = normalizeName(deck.getTournament());
        String veknId = StringUtils.trimToNull(deck.getVeknId());
        String eventId = StringUtils.trimToNull(deck.getEventId());
        Map<Integer, Integer> incomingCards = cardCounts(cards);
        if ((deck.getCreationDate() == null) || ((eventId == null)
                && (incomingCards.isEmpty() && ((veknId == null) || tournament.isEmpty())))) {
            return List.of();
        }
        var start = deck.getCreationDate().toLocalDate().atStartOfDay();
        // Legacy tournament decks without a position are winners.
        List<DeckEntity> candidates = deckRepository.findActiveTournamentDecksBetween(start, start.plusDays(1)).stream()
                .filter(existing -> existing.getCreationDate() != null && existing.getCreationDate().toLocalDate().equals(start.toLocalDate())
                        && !Objects.equals(existing.getId(), deck.getId())
                        && Objects.equals(Objects.requireNonNullElse(existing.getPosition(), 1), Objects.requireNonNullElse(deck.getPosition(), 1)))
                .toList();
        List<String> cardCandidateIds = candidates.stream()
                .filter(existing -> !incomingCards.isEmpty() && compatiblePlayer(veknId, existing))
                .map(DeckEntity::getId).toList();
        Map<String, List<DeckCardEntity>> existingCards = new HashMap<>();
        if (!cardCandidateIds.isEmpty()) {
            for (DeckCardEntity card : deckCardRepository.findByIdDeckIdIn(cardCandidateIds)) {
                existingCards.computeIfAbsent(card.getId().getDeckId(), ignored -> new ArrayList<>()).add(card);
            }
        }
        List<DeckEntity> duplicates = new ArrayList<>();
        for (DeckEntity existing : candidates) {
            String existingEventId = StringUtils.trimToNull(existing.getEventId());
            boolean samePlayer = (veknId != null) && veknId.equals(StringUtils.trimToNull(existing.getVeknId()));
            boolean sameResult = (eventId != null && existingEventId != null)
                    ? eventId.equals(existingEventId)
                    : (!tournament.isEmpty() && tournament.equals(normalizeName(existing.getTournament()))
                    && samePlayer);
            // A missing player ID cannot prove ownership. Block an identical legacy list
            // conservatively, but callers must not enrich it without a matching event.
            boolean sameCardsAndPlayer = !incomingCards.isEmpty() && compatiblePlayer(veknId, existing)
                    && incomingCards.equals(cardCounts(existingCards.getOrDefault(existing.getId(), List.of())));
            if (sameResult || sameCardsAndPlayer) {
                duplicates.add(existing);
            }
        }
        if (!duplicates.isEmpty()) {
            log.debug("Skipping duplicate deck {}, existing decks {} match a tournament result on {} in position {}",
                    deck.getId(), duplicates.stream().map(DeckEntity::getId).toList(), start.toLocalDate(), deck.getPosition());
        }
        return duplicates;
    }

    private boolean compatiblePlayer(String veknId, DeckEntity existing) {
        String existingId = StringUtils.trimToNull(existing.getVeknId());
        return (veknId == null) || (existingId == null) || veknId.equals(existingId);
    }

    private static Map<Integer, Integer> cardCounts(Collection<DeckCardEntity> cards) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (DeckCardEntity card : cards) {
            if (card.getId() == null || card.getId().getCardId() == null || card.getNumber() == null || card.getNumber() <= 0) {
                return Map.of();
            }
            counts.merge(card.getId().getCardId(), card.getNumber(), Integer::sum);
        }
        return counts;
    }

    private static String normalizeName(String value) {
        return NAME_SEPARATORS.matcher(StringUtils.stripAccents(StringUtils.defaultString(value)))
                .replaceAll(" ").trim().toLowerCase(Locale.ROOT);
    }

    private Action actionFor(DeckEntity actual, TournamentSchedulerOwner incoming) {
        if (actual == null) {
            return Action.IMPORT;
        }
        if (incoming == TournamentSchedulerOwner.ARCHON && (Boolean.TRUE.equals(actual.getVerified())
                || actual.getSchedulerOwner() == TournamentSchedulerOwner.TWDA)) {
            return Action.ENRICH_METADATA;
        }
        if (Boolean.TRUE.equals(actual.getVerified())) {
            return incoming == TournamentSchedulerOwner.TWDA ? Action.REPORT_VERIFIED : null;
        }
        return incoming.canReplace(actual.getSchedulerOwner()) ? Action.IMPORT : null;
    }
}
