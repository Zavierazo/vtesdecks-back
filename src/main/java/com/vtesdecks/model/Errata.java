package com.vtesdecks.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;

@Getter
@RequiredArgsConstructor
public enum Errata {
    ANKARA_CITADEL(100071, LocalDate.of(2019, 9, 1), "The cost reducer no longer applies to action modifiers."),
    UNA(201407, LocalDate.of(2019, 9, 1), "Fortitude cost reducer is now only for combat cards."),
    PARITY_SHIFT(101353, LocalDate.of(2019, 9, 1), "It does no longer takes a variable amount pool (1-5) from the target of the vote, but rather a fixed amount of pool, namely 3 pool regardless of the number of players at the table."),
    PENTEX_SUBVERSION(101384, LocalDate.of(2019, 9, 1), "It does not prevent a vampire to act anymore, so it is less powerful."),
    ASHUR_TABLETS(100106, LocalDate.of(2024, 2, 1), "Only one Ashur Tablets may be played each turn."),
    EMERALD_LEGIONNAIRE(100634, LocalDate.of(2024, 2, 1), "You cannot use the Emerald Legionnaire’s ability during your unlock phase if you control as many or more copies of them as ready unique Harbinger of Skulls."),
    CARDINAL_BENEDICTION(100294, LocalDate.of(2050, 1, 1), "Now prevents non-Sabbat vampires from casting votes and ballots (instead of affecting only Camarilla vampires)."),
    MIND_NUMB(101211, LocalDate.of(1, 1, 1), "Now stuns the minion. Stun:  lock a minion and put a stun counter on them. A minion with one or more stun counters does not unlock as normal at the beginning of their controller’s unlock phase; during that unlock phase, burn all stun counters they had at the beginning of the turn."),
    PRIVATE_AUDIENCE(101490, LocalDate.of(1, 1, 1), "Now costs no blood (instead of 1 blood)."),
    RUTOR_HAND(101664, LocalDate.of(1, 1, 1), "Now enters play locked and requires to lock the card to unlock."),
    WALL_STREET_NIGHT(102142, LocalDate.of(1, 1, 1), "The card is now locked when the minion attempts the action to move counters.");

    private final Integer id;
    private final LocalDate effectiveDate;
    private final String description;

    public static Errata findErrata(Integer id, LocalDate deckDate) {
        for (Errata errata : Errata.values()) {
            if (errata.getId().equals(id) && LocalDate.now().isAfter(errata.getEffectiveDate()) && deckDate.isBefore(errata.getEffectiveDate())) {
                return errata;
            }
        }
        return null;
    }

}
