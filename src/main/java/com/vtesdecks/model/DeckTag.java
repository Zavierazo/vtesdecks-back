package com.vtesdecks.model;

import com.google.common.collect.Lists;
import com.vtesdecks.cache.indexable.Crypt;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.cache.indexable.Library;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

@AllArgsConstructor
public enum DeckTag {
    TWO_PLAYER("2player", 0, crypt -> true, library -> false, deck -> deck.getLimitedFormat() != null && deck.getLimitedFormat().startsWith("Two-player") && !deck.getLimitedFormat().endsWith("(Custom)")),
    V5("v5", 0, crypt -> true, library -> false, deck -> deck.getLimitedFormat() != null && deck.getLimitedFormat().startsWith("V5") && !deck.getLimitedFormat().endsWith("(Custom)")),
    BLEED("bleed", 15, crypt -> hasTaint(crypt, CryptTaint.BLEED), library -> hasTaint(library, LibraryTaint.BLEED), deck -> true),
    STEALTH("stealth", 15, crypt -> hasTaint(crypt, CryptTaint.STEALTH), library -> hasTaint(library, LibraryTaint.NEGATIVE_INTERCEPT), deck -> true),
    BLOCK("block", 12, crypt -> hasTaint(crypt, CryptTaint.INTERCEPT), DeckTag::isBlockLibrary, deck -> true),
    RUSH("rush", 8, crypt -> hasTaint(crypt, CryptTaint.COMBAT), library -> hasTaint(library, LibraryTaint.COMBAT), deck -> true),
    COMBAT("combat", 20, crypt -> hasTaint(crypt, CryptTaint.STRENGTH, CryptTaint.PRESS, CryptTaint.ADDITIONAL_STRIKE, CryptTaint.PREVENT), DeckTag::isCombatLibrary, deck -> true),
    ALLY("ally", 8, crypt -> false, DeckTag::isAllyLibrary, deck -> true),
    RAMP("ramp", 10, crypt -> hasTaint(crypt, CryptTaint.ADD_BLOOD), library -> hasTaint(library, LibraryTaint.ADD_BLOOD), deck -> true),
    MMPA("mmpa", 10, DeckTag::isMmpaCrypt, DeckTag::isMmpaLibrary, deck -> deck.getStats().getMaster() > 15),
    SWARM("swarm", 8, crypt -> false, library -> hasTaint(library, LibraryTaint.CREATE_VAMPIRE), deck -> true),
    VOTE("vote", 8, crypt -> false, DeckTag::isVoteLibrary, deck -> deck.getStats().getPoliticalAction() > 5);


    @Getter
    private final String tag;
    @Getter
    private final Integer threshold;
    @Getter
    private final Predicate<Crypt> cryptTest;
    @Getter
    private final Predicate<Library> libraryTest;
    @Getter
    private final Predicate<Deck> deckTest;


    private static boolean hasTaint(Crypt crypt, CryptTaint... taints) {
        return Stream.of(taints).anyMatch(taint -> crypt.getTaints().contains(taint.getName()));
    }

    private static boolean hasTaint(Library library, LibraryTaint... taints) {
        return Stream.of(taints).anyMatch(taint -> library.getTaints().contains(taint.getName()));
    }

    private static final List<Integer> MMPA_CRYPT = Lists.newArrayList(
            200107, //Anson
            200305, //Cybele
            201035, //Nana Buruku
            200611, //Huitzilopochtli
            200644 //Isanwayen
    );
    private static final List<Integer> MMPA_LIBRARY = Lists.newArrayList(
            101355, //Parthenon, The
            101663 //Rumors of Gehenna
    );
    private static final List<Integer> ALLY_LIBRARY = Lists.newArrayList(
            100709, //FBI Special Affairs Division
            102079 //Unmasking, The
    );

    private static boolean isMmpaCrypt(Crypt crypt) {
        return MMPA_CRYPT.contains(crypt.getId());
    }

    private static boolean isAllyLibrary(Library library) {
        if (library.getType().contains("Ally")) {
            return true;
        }
        return ALLY_LIBRARY.contains(library.getId());
    }

    private static boolean isBlockLibrary(Library library) {
        if (library.getType().contains("Reaction")) {
            return hasTaint(library, LibraryTaint.UNLOCK) || (hasTaint(library, LibraryTaint.POSITIVE_INTERCEPT) && !hasTaint(library, LibraryTaint.CHANGE_TARGET));
        }
        return hasTaint(library, LibraryTaint.POSITIVE_INTERCEPT);
    }

    private static boolean isMmpaLibrary(Library library) {
        return MMPA_LIBRARY.contains(library.getId());
    }

    private static boolean isCombatLibrary(Library library) {
        if (library.getType().contains("Combat")) {
            return true;
        }
        return hasTaint(library, LibraryTaint.STRENGTH, LibraryTaint.AGGRAVATED, LibraryTaint.PRESS, LibraryTaint.PREVENT, LibraryTaint.ADDITIONAL_STRIKE);
    }

    private static boolean isVoteLibrary(Library library) {
        if (library.getType().contains("Political Action")) {
            return true;
        }
        return hasTaint(library, LibraryTaint.VOTES);
    }
}
