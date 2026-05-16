package com.vtesdecks.cache.factory;

import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.cache.redis.entity.ArchetypeKeyCard;
import com.vtesdecks.cache.redis.entity.DeckArchetype;
import com.vtesdecks.jpa.entity.DeckArchetypeEntity;
import com.vtesdecks.model.DeckQuery;
import com.vtesdecks.service.DeckKeyCardsService;
import com.vtesdecks.service.DeckService;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Mapper(componentModel = "spring")
public abstract class DeckArchetypeFactory {

    @Autowired
    private DeckService deckService;

    @Autowired
    private DeckKeyCardsService deckKeyCardsService;

    public abstract DeckArchetype getDeckArchetype(DeckArchetypeEntity deckArchetypeEntity);

    @AfterMapping
    protected void afterMapping(@MappingTarget DeckArchetype deckArchetype, DeckArchetypeEntity entity) {
        deckArchetype.setDeckCount(deckCount(DeckQuery.builder().archetype(entity.getId()).build()));
        deckArchetype.setTournamentCount(deckCount(DeckQuery.builder().archetype(entity.getId()).type(DeckType.TOURNAMENT).build()));
        deckArchetype.setTournament90Count(deckCount(DeckQuery.builder().archetype(entity.getId()).type(DeckType.TOURNAMENT).creationDate(LocalDate.now().minusDays(90)).build()));
        deckArchetype.setTournament180Count(deckCount(DeckQuery.builder().archetype(entity.getId()).type(DeckType.TOURNAMENT).creationDate(LocalDate.now().minusDays(180)).build()));
        deckArchetype.setTournament365Count(deckCount(DeckQuery.builder().archetype(entity.getId()).type(DeckType.TOURNAMENT).creationDate(LocalDate.now().minusDays(365)).build()));
        deckArchetype.setTournament730Count(deckCount(DeckQuery.builder().archetype(entity.getId()).type(DeckType.TOURNAMENT).creationDate(LocalDate.now().minusDays(730)).build()));
        if (entity.getDeckId() != null) {
            Deck deck = deckService.getDeck(entity.getDeckId());
            if (deck != null && deck.getStats() != null) {
                deckArchetype.setPrice(deck.getStats().getPrice());
                deckArchetype.setCurrency(deck.getStats().getCurrency());
            }
        }
        computeKeyCards(deckArchetype, entity);
    }

    private void computeKeyCards(DeckArchetype deckArchetype, DeckArchetypeEntity entity) {
        if (entity.getId() == null || entity.getId() == 0) {
            return;
        }
        List<Deck> decks;
        try (ResultSet<Deck> deckResultSet = deckService.getDecks(
                DeckQuery.builder().archetype(entity.getId()).type(DeckType.TOURNAMENT).build())) {
            decks = deckResultSet.stream().toList();
        }
        List<ArchetypeKeyCard> keyCards = deckKeyCardsService.computeKeyCards(decks, DeckKeyCardsService.MIN_APPEARANCE_THRESHOLD);
        deckArchetype.setKeyCards(keyCards.isEmpty() ? null : keyCards);
    }

    private long deckCount(DeckQuery query) {
        try (ResultSet<Deck> deckResultSet = deckService.getDecks(query)) {
            return deckResultSet.stream().count();
        }
    }
}
