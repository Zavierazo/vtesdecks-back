package com.vtesdecks.cache;

import com.googlecode.cqengine.IndexedCollection;
import com.vtesdecks.cache.indexable.DeckCard;
import com.vtesdecks.cache.indexable.DeckSummary;
import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.cache.indexable.deck.Stats;
import com.vtesdecks.model.DeckQuery;
import com.vtesdecks.model.DeckSort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeckIndexTest {
    private DeckIndex index;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        index = new DeckIndex();
        index.setUp();
        DeckCardIndex cards = new DeckCardIndex();
        cards.setUp();
        ReflectionTestUtils.setField(index, "deckCardIndex", cards);
        IndexedCollection<DeckSummary> decks = (IndexedCollection<DeckSummary>) ReflectionTestUtils.getField(index, "decks");
        for (int number = 1; number <= 4; number++) {
            decks.add(deck(number));
            cards.getRepository().add(DeckCard.builder()
                    .deckId("deck-" + number).id(200001).number(number).build());
        }
    }

    @ParameterizedTest
    @EnumSource(DeckSort.class)
    void preservesEverySortAndExcludesPrivateDecks(DeckSort sort) {
        List<String> expected = switch (sort) {
            case NAME, OLDEST, CHEAPEST -> List.of("deck-1", "deck-2", "deck-3");
            default -> List.of("deck-3", "deck-2", "deck-1");
        };
        assertEquals(expected, ids(DeckQuery.builder().order(sort).build()));
    }

    @ParameterizedTest
    @CsvSource({"name,deck 2", "tournament,event 2", "place,city 2",
            "place,country 2", "limitedFormat,format 2"})
    void preservesSubstringFilters(String field, String text) {
        DeckQuery query = DeckQuery.builder().build();
        ReflectionTestUtils.setField(query, field, text);
        assertEquals(List.of("deck-2"), ids(query));
    }

    @Test
    void preservesInclusiveYearAndPlayerRanges() {
        assertEquals(List.of("deck-2"), ids(DeckQuery.builder().minYear(2022).maxYear(2022).build()));
        assertEquals(List.of("deck-2"), ids(DeckQuery.builder().minPlayers(20).maxPlayers(20).build()));
    }

    @Test
    void preservesCreationDateAndCardQuantityFilters() {
        assertEquals(List.of("deck-3", "deck-2"), ids(DeckQuery.builder()
                .creationDate(LocalDate.of(2022, 1, 1)).build()));
        assertEquals(List.of("deck-3", "deck-2"), ids(DeckQuery.builder()
                .cards(List.of("200001=2")).build()));
    }

    @ParameterizedTest
    @CsvSource({"master", "action", "political", "retainer", "equipment",
            "ally", "modifier", "combat", "reaction", "event"})
    void preservesAbsoluteAndPercentageFilters(String field) {
        for (DeckQuery.ProportionType type : DeckQuery.ProportionType.values()) {
            DeckQuery query = DeckQuery.builder().proportionType(type).build();
            int value = (type == DeckQuery.ProportionType.ABSOLUTE) ? 2 : 4;
            ReflectionTestUtils.setField(query, field, new DeckQuery.CardProportion(value, value));
            assertEquals(List.of("deck-2"), ids(query), field + " " + type);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void popularSortPreservesTieBreakersAndIndexUpdates() {
        IndexedCollection<DeckSummary> decks = (IndexedCollection<DeckSummary>) ReflectionTestUtils.getField(index, "decks");
        for (int number = 1; number <= 3; number++) {
            DeckSummary replacement = deck(number);
            replacement.setViewsLastMonth(0L);
            replacement.setViews(10L);
            replacement.setRate(null);
            decks.update(List.of(index.get(replacement.getId())), List.of(replacement));
        }
        DeckQuery query = DeckQuery.builder().order(DeckSort.POPULAR).build();
        assertEquals(List.of("deck-3", "deck-2", "deck-1"), ids(query));
        DeckSummary replacement = deck(1);
        replacement.setViewsLastMonth(0L);
        replacement.setViews(11L);
        decks.update(List.of(index.get(replacement.getId())), List.of(replacement));
        assertEquals(List.of("deck-1", "deck-3", "deck-2"), ids(query));
        decks.remove(replacement);
        assertEquals(List.of("deck-3", "deck-2"), ids(query));
    }

    private List<String> ids(DeckQuery query) {
        try (var result = index.selectAll(query)) {
            return result.stream().map(DeckSummary::getId).toList();
        }
    }

    private DeckSummary deck(int number) {
        DeckSummary deck = new DeckSummary();
        deck.setId("deck-" + number);
        deck.setName("Deck " + number);
        deck.setType(DeckType.COMMUNITY);
        deck.setPublished(number != 4);
        deck.setTournament("Event " + number);
        deck.setPlace("City " + number);
        deck.setCountry("Country " + number);
        deck.setLimitedFormat("Format " + number);
        deck.setYear(2020 + number);
        deck.setPlayers(number * 10);
        deck.setCreationDate(LocalDate.of(2020 + number, 1, 1).atStartOfDay());
        deck.setModifyDate(deck.getCreationDate());
        deck.setVotes(number);
        deck.setRate((double) number);
        deck.setViews((long) number);
        deck.setViewsLastMonth((long) number);
        deck.setComments((long) number);
        deck.setClans(Set.of());
        deck.setDisciplines(Set.of());
        deck.setGroups(Set.of());
        deck.setTags(Set.of());
        deck.setFavoriteUsers(Set.of());
        Stats stats = new Stats();
        stats.setLibrary(50);
        stats.setPrice(BigDecimal.valueOf(number));
        for (String field : List.of("master", "action", "politicalAction", "retainer", "equipment",
                "ally", "actionModifier", "combat", "reaction", "event")) {
            ReflectionTestUtils.setField(stats, field, number);
        }
        deck.setStats(stats);
        return deck;
    }
}
