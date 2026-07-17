package com.vtesdecks.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReactionType {
    // Deck reactions (display order)
    WOULD_PLAY("would_play", ReactionTargetType.DECK),
    TOURNAMENT_WORTHY("tournament_worthy", ReactionTargetType.DECK),
    ORIGINAL_BREW("original_brew", ReactionTargetType.DECK),
    MIND_BLOWING("mind_blowing", ReactionTargetType.DECK),
    SPICY("spicy", ReactionTargetType.DECK),
    TOO_GREEDY("too_greedy", ReactionTargetType.DECK),
    // Comment reactions (display order)
    THUMBS_UP("thumbs_up", ReactionTargetType.COMMENT),
    THUMBS_DOWN("thumbs_down", ReactionTargetType.COMMENT),
    HEART("heart", ReactionTargetType.COMMENT),
    LAUGH("laugh", ReactionTargetType.COMMENT),
    WOW("wow", ReactionTargetType.COMMENT),
    THANKS("thanks", ReactionTargetType.COMMENT),
    HUNDRED("hundred", ReactionTargetType.COMMENT);

    @JsonValue
    private final String key;
    private final ReactionTargetType targetType;
}
