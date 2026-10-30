package com.vtesdecks.scheduler.tournament;

import com.vtesdecks.jpa.repositories.ArchonUserRepository.PlayerName;
import com.vtesdecks.scheduler.tournament.helpers.TournamentPlayerLookup;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class TournamentPlayerLookupTest {
    @Test
    void matchesOrderedWordPrefixesIgnoringAccentsAndExtraSpaces() {
        var lookup = new TournamentPlayerLookup(List.of(new Player("Gregory Wílliams", "003120065")));
        assertEquals("003120065", lookup.findVeknId(" Grég  Williams "));
    }

    @Test
    void allowsIntermediateNamesButPreservesWordOrderAndDoesNotReuseWords() {
        var lookup = new TournamentPlayerLookup(List.of(new Player("Gregory Alan Williams", "003120065")));
        assertEquals("003120065", lookup.findVeknId("Greg Williams"));
        assertNull(lookup.findVeknId("Williams Greg"));
        assertNull(lookup.findVeknId("Greg Greg Williams"));
    }

    @Test
    void rejectsAmbiguityBetweenContainsAndPrefixCandidates() {
        var lookup = new TournamentPlayerLookup(List.of(new Player("Greg Williams Junior", "001"), new Player("Gregory Williams", "002")));
        assertNull(lookup.findVeknId("Greg Williams"));
    }

    @Test
    void stillPrefersTheExactNameOverAPrefixCandidate() {
        var lookup = new TournamentPlayerLookup(List.of(new Player("Greg Williams", "001"), new Player("Gregory Williams", "002")));
        assertEquals("001", lookup.findVeknId("Greg Williams"));
    }

    @Test
    void keepsAccentVariantsWithDifferentIdsAmbiguous() {
        var lookup = new TournamentPlayerLookup(List.of(new Player("Gregory Wílliams", "001"), new Player("Gregory Williams", "002")));
        assertNull(lookup.findVeknId("Greg Williams"));
        assertNull(lookup.findVeknId("Gregory Williams"));
    }

    @Test
    void findsExactNamesIgnoringCaseAccentsAndSurroundingWhitespace() {
        var lookup = new TournamentPlayerLookup(List.of(new Player(" Álex Smith ", "003120065")));
        assertEquals("003120065", lookup.findVeknId(" ALEX SMITH "));
    }

    @Test
    void matchesContainsInBothDirections() {
        var shortName = new TournamentPlayerLookup(List.of(new Player("Pedro Coelho", "003120065")));
        var longName = new TournamentPlayerLookup(List.of(new Player("Pedro Coelho Mendes Bueno Barbosa", "003120065")));
        assertEquals("003120065", shortName.findVeknId("Pedro Coelho Mendes Bueno Barbosa"));
        assertEquals("003120065", longName.findVeknId("Pedro Coelho"));
    }

    @Test
    void prefersAnExactMatchOverOtherContainingNames() {
        var lookup = new TournamentPlayerLookup(List.of(new Player("Pedro Coelho", "001"), new Player("Pedro Coelho Mendes", "002")));
        assertEquals("001", lookup.findVeknId("Pedro Coelho"));
    }

    @Test
    void leavesExactHomonymsUnresolvedWithoutFallingBackToContains() {
        var lookup = new TournamentPlayerLookup(List.of(new Player("Pedro Coelho", "001"), new Player(" PEDRO COELHO ", "002"),
                new Player("Pedro Coelho Mendes", "003")));
        assertNull(lookup.findVeknId("Pedro Coelho"));
    }

    @Test
    void leavesAmbiguousContainsMatchesUnresolved() {
        var lookup = new TournamentPlayerLookup(List.of(new Player("John Smith Junior", "001"), new Player("John Smith Senior", "002")));
        assertNull(lookup.findVeknId("John Smith"));
    }

    @Test
    void requiresWholeWordsAndAtLeastTwoWordsForContains() {
        var lookup = new TournamentPlayerLookup(List.of(new Player("Pedro", "001"), new Player("Barb", "002"),
                new Player("Pedro Coelho Mendes Bueno Barbosa", "003"), new Player("Joann Lee", "004")));
        assertEquals("003", lookup.findVeknId("Pedro Coelho"));
        assertNull(lookup.findVeknId("Ann Lee"));
        assertNull(new TournamentPlayerLookup(List.of(new Player("Pedro", "001"))).findVeknId("Pedro Coelho"));
        assertNull(new TournamentPlayerLookup(List.of(new Player("Pedro Coelho", "001"))).findVeknId("Pedro"));
    }

    @Test
    void ignoresEmptyNamesAndIdsAndDoesNotGuessNicknamesOrInterpretWildcards() {
        var lookup = new TournamentPlayerLookup(List.of(new Player(" ", "001"), new Player(null, "002"),
                new Player("Missing ID", " "), new Player("Unknown ID", null),
                new Player("Tomasz Kowalewski", "003"), new Player("Pat X Smith", "004")));
        for (String author : List.of("", " ", "Missing ID", "Unknown ID", "Tomek Kowalewski", "Pat %Smith", "Pat _Smith")) {
            assertNull(lookup.findVeknId(author));
        }
        assertNull(lookup.findVeknId(null));
    }

    @Test
    void canBeSharedByParallelWorkersWithoutChangingResults() {
        var lookup = new TournamentPlayerLookup(List.of(new Player("Pedro Coelho Mendes Bueno Barbosa", "003120065")));
        IntStream.range(0, 100).parallel().forEach(index -> assertEquals("003120065", lookup.findVeknId("Pedro Coelho")));
    }

    private record Player(String name, String veknId) implements PlayerName {
        public String getName() {
            return name;
        }

        public String getVeknId() {
            return veknId;
        }
    }
}
