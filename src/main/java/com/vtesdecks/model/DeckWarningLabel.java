package com.vtesdecks.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor

public enum DeckWarningLabel {
    SPOILER("vtes.warning.spoiler"),
    TOURNAMENT_LEGAL_DATE("vtes.warning.tournament_legal_date"),
    BANNED_CARDS("vtes.warning.banned_cards");

    private final String label;
}
