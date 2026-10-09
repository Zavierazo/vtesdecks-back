package com.vtesdecks.scheduler.tournament.helpers;

import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.jpa.entity.DeckEntity;
import com.vtesdecks.jpa.entity.TournamentSchedulerOwner;
import com.vtesdecks.jpa.repositories.DeckRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/** Resolves identity and ownership before building or persisting an import. */
@Slf4j
@RequiredArgsConstructor
public class TournamentImportPolicy {
    private final DeckRepository deckRepository;
    private final TransactionTemplate transactionTemplate;

    public enum Action {
        IMPORT, ENRICH_FINAL_RESULT, REPORT_VERIFIED
    }

    /** Cheap rejection only; allowed imports must still resolve their identity in execute. */
    public boolean shouldSkipFetch(String id, TournamentSchedulerOwner incoming) {
        return deckRepository.findById(id)
                .map(actual -> actual.getType() != DeckType.TOURNAMENT || Boolean.TRUE.equals(actual.getDeleted())
                        || actionFor(actual, incoming) == null)
                .orElse(false);
    }

    public void execute(String id, String eventId, Integer position, TournamentSchedulerOwner incoming,
                        BiConsumer<DeckEntity, Action> importer) {
        transactionTemplate.executeWithoutResult(status -> {
            Map<String, DeckEntity> matches = new LinkedHashMap<>();
            deckRepository.findById(id).ifPresent(deck -> matches.put(deck.getId(), deck));
            if (StringUtils.isNotBlank(eventId) && position != null) {
                deckRepository.findByTypeAndEventIdAndPositionAndDeletedFalse(DeckType.TOURNAMENT, eventId, position)
                        .forEach(deck -> matches.put(deck.getId(), deck));
            }
            if (matches.size() > 1) {
                log.warn("Skipping {} import {}, conflicting tournament deck identities: {}", incoming, id, matches.keySet());
                return;
            }
            DeckEntity actual = matches.values().stream().findFirst().orElse(null);
            if (actual != null) {
                if (actual.getType() != DeckType.TOURNAMENT || Boolean.TRUE.equals(actual.getDeleted())
                        || (StringUtils.isNotBlank(eventId) && StringUtils.isNotBlank(actual.getEventId())
                        && !Objects.equals(eventId, actual.getEventId()))
                        || (position != null && actual.getPosition() != null && !Objects.equals(position, actual.getPosition()))) {
                    log.warn("Skipping {} import {}, existing deck {} has an incompatible identity or is deleted", incoming, id, actual.getId());
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

    private Action actionFor(DeckEntity actual, TournamentSchedulerOwner incoming) {
        if (actual == null) {
            return Action.IMPORT;
        }
        if (Boolean.TRUE.equals(actual.getVerified())) {
            return incoming == TournamentSchedulerOwner.TWDA ? Action.REPORT_VERIFIED : null;
        }
        if (incoming == TournamentSchedulerOwner.ARCHON && actual.getSchedulerOwner() == TournamentSchedulerOwner.TWDA) {
            return Action.ENRICH_FINAL_RESULT;
        }
        return incoming.canReplace(actual.getSchedulerOwner()) ? Action.IMPORT : null;
    }
}
