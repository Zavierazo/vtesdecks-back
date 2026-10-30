package com.vtesdecks.jpa.entity;

public enum TournamentSchedulerOwner {
    TWDA(3),
    ARCHON(2),
    // Retained for persisted decks from the retired importer; active sources may take ownership.
    ETERNAL_VIGILANCE(1);

    private final int priority;

    TournamentSchedulerOwner(int priority) {
        this.priority = priority;
    }

    public boolean canReplace(TournamentSchedulerOwner owner) {
        return owner != null && priority >= owner.priority;
    }
}
