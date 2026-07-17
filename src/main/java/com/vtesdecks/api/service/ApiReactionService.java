package com.vtesdecks.api.service;

import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.enums.ReactionTargetType;
import com.vtesdecks.enums.ReactionType;
import com.vtesdecks.jpa.entity.CommentEntity;
import com.vtesdecks.jpa.entity.ReactionEntity;
import com.vtesdecks.jpa.repositories.CommentRepository;
import com.vtesdecks.jpa.repositories.ReactionRepository;
import com.vtesdecks.messaging.MessageProducer;
import com.vtesdecks.model.api.ApiReactionSummary;
import com.vtesdecks.service.DeckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiReactionService {
    private final ReactionRepository reactionRepository;
    private final CommentRepository commentRepository;
    private final DeckService deckService;
    private final MessageProducer messageProducer;

    public boolean reactDeck(Integer userId, String deckId, ReactionType reaction, Boolean active) {
        if (userId == null || deckId == null || reaction == null || active == null) {
            return false;
        }
        if (reaction.getTargetType() != ReactionTargetType.DECK) {
            log.warn("Reaction {} is not valid for decks (user {})", reaction, userId);
            return false;
        }
        Deck deck = deckService.getDeck(deckId);
        if (deck == null) {
            log.warn("Deck {} not found for reaction {} by user {}", deckId, reaction, userId);
            return false;
        }
        if (toggle(userId, ReactionTargetType.DECK, deckId, reaction, active)) {
            // Refresh the cached deck so its featured reaction stays current on the deck list
            messageProducer.publishDeckSync(deckId);
        }
        return true;
    }

    public boolean reactComment(Integer userId, Integer commentId, ReactionType reaction, Boolean active) {
        if (userId == null || commentId == null || reaction == null || active == null) {
            return false;
        }
        if (reaction.getTargetType() != ReactionTargetType.COMMENT) {
            log.warn("Reaction {} is not valid for comments (user {})", reaction, userId);
            return false;
        }
        CommentEntity comment = commentRepository.findById(commentId).orElse(null);
        if (comment == null || Boolean.TRUE.equals(comment.getDeleted())) {
            log.warn("Comment {} not found for reaction {} by user {}", commentId, reaction, userId);
            return false;
        }
        toggle(userId, ReactionTargetType.COMMENT, String.valueOf(commentId), reaction, active);
        return true;
    }

    public List<ApiReactionSummary> getReactions(ReactionTargetType targetType, String targetId, Integer userId) {
        return summarize(reactionRepository.findByIdTargetTypeAndIdTargetId(targetType, targetId), userId);
    }

    public Map<String, List<ApiReactionSummary>> getReactionsBulk(ReactionTargetType targetType, Collection<String> targetIds, Integer userId) {
        Map<String, List<ApiReactionSummary>> result = new HashMap<>();
        if (targetIds == null || targetIds.isEmpty()) {
            return result;
        }
        Map<String, List<ReactionEntity>> byTarget = new HashMap<>();
        for (ReactionEntity reaction : reactionRepository.findByIdTargetTypeAndIdTargetIdIn(targetType, targetIds)) {
            byTarget.computeIfAbsent(reaction.getId().getTargetId(), key -> new ArrayList<>()).add(reaction);
        }
        for (Map.Entry<String, List<ReactionEntity>> entry : byTarget.entrySet()) {
            result.put(entry.getKey(), summarize(entry.getValue(), userId));
        }
        return result;
    }

    /**
     * @return true when the reaction state actually changed (activated or deactivated)
     */
    private boolean toggle(Integer userId, ReactionTargetType targetType, String targetId, ReactionType reaction, boolean active) {
        ReactionEntity.ReactionId id = new ReactionEntity.ReactionId(userId, targetType, targetId, reaction);
        boolean exists = reactionRepository.existsById(id);
        if (active && !exists) {
            ReactionEntity entity = new ReactionEntity();
            entity.setId(id);
            reactionRepository.save(entity);
            return true;
        }
        if (!active && exists) {
            reactionRepository.deleteById(id);
            return true;
        }
        return false;
    }

    private List<ApiReactionSummary> summarize(List<ReactionEntity> reactions, Integer userId) {
        Map<ReactionType, ApiReactionSummary> byType = new EnumMap<>(ReactionType.class);
        for (ReactionEntity reaction : reactions) {
            ApiReactionSummary summary = byType.computeIfAbsent(reaction.getId().getReaction(),
                    type -> new ApiReactionSummary(type, 0, false));
            summary.setCount(summary.getCount() + 1);
            if (userId != null && userId.equals(reaction.getId().getUser())) {
                summary.setReacted(true);
            }
        }
        List<ApiReactionSummary> result = new ArrayList<>();
        for (ReactionType type : ReactionType.values()) {
            if (byType.containsKey(type)) {
                result.add(byType.get(type));
            }
        }
        return result;
    }
}
