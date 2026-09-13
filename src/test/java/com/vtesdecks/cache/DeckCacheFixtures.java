package com.vtesdecks.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.cache.indexable.DeckWarning;
import com.vtesdecks.cache.indexable.deck.ClanStat;
import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.cache.indexable.deck.DeckUser;
import com.vtesdecks.cache.indexable.deck.DisciplineStat;
import com.vtesdecks.cache.indexable.deck.Stats;
import com.vtesdecks.cache.indexable.deck.card.Card;
import com.vtesdecks.jpa.entity.CardErrataEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public final class DeckCacheFixtures {
    private DeckCacheFixtures() {
    }

    public static Deck deck() throws Exception {
        Deck deck = new Deck();
        deck.setId("fixture-deck");
        deck.setName("A deck with full details");
        deck.setDescription("Full description with accents: café 日本語");
        deck.setUrl("https://example.com/deck");
        deck.setSource("fixture");
        deck.setType(DeckType.COMMUNITY);
        deck.setPublished(true);
        deck.setCreationDate(LocalDateTime.of(2026, 1, 1, 12, 0));
        deck.setModifyDate(deck.getCreationDate());
        deck.setViews(10L);
        deck.setViewsLastMonth(5L);
        deck.setVotes(2);
        deck.setRate(4.5);
        deck.setComments(1L);
        deck.setUser(DeckUser.builder().id(42).username("owner").displayName("Owner")
                .profileImage("https://example.com/avatar").roles(List.of("ROLE_USER")).build());
        deck.setClans(Set.of("Ventrue"));
        deck.setDisciplines(Set.of("dom", "for"));
        deck.setGroups(Set.of(5, 6));
        deck.setTags(Set.of("political", "bleed"));
        deck.setFavoriteUsers(Set.of(42, 43));
        deck.setClanIcons(Set.of("ventrue"));
        deck.setDisciplineIcons(Set.of("DOM", "FOR"));
        deck.setCrypt(List.of(Card.builder().id(200001).number(4).build()));
        deck.setLibrary(List.of(Card.builder().id(100001).number(8).build()));
        Stats stats = new Stats();
        stats.setCrypt(4);
        stats.setLibrary(8);
        stats.setPrice(new BigDecimal("25.50"));
        stats.setCurrency("EUR");
        var discipline = new DisciplineStat();
        discipline.setDisciplines(Set.of("dom"));
        discipline.setInferior(1);
        discipline.setSuperior(3);
        stats.setCryptDisciplines(List.of(discipline));
        stats.setLibraryDisciplines(List.of(discipline));
        var clan = new ClanStat();
        clan.setClans(Set.of("Ventrue"));
        clan.setNumber(8);
        stats.setLibraryClans(List.of(clan));
        deck.setStats(stats);
        deck.setL2Norm(Math.sqrt(80));
        deck.setExtra(new ObjectMapper().readTree("{\"advent\":1,\"nested\":{\"tags\":[\"b\",\"a\"]},\"notes\":\"all extras retained\"}"));
        deck.setWarnings(Set.of(DeckWarning.builder().label("Card changed").date(LocalDate.of(2026, 1, 2)).build()));
        CardErrataEntity errata = new CardErrataEntity();
        errata.setId(1);
        errata.setCardId(100001);
        errata.setDescription("Updated card text");
        errata.setEffectiveDate(LocalDate.of(2026, 1, 2));
        errata.setRequiresWarning(true);
        deck.setErratas(List.of(errata));
        return deck;
    }
}
