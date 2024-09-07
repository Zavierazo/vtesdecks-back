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
    EMERALD_LEGIONNAIRE(100634, LocalDate.of(2024, 2, 1), "You cannot use the Emerald Legionnaire’s ability during your unlock phase if you control as many or more copies of them as ready unique Harbinger of Skulls.");

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
