package com.vtesdecks.cache.factory;

import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.cache.DeckIndex;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.cache.redis.entity.DeckArchetype;
import com.vtesdecks.jpa.entity.DeckArchetypeEntity;
import com.vtesdecks.model.DeckQuery;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

@Slf4j
@Mapper(componentModel = "spring")
public abstract class DeckArchetypeFactory {

    @Autowired
    private DeckIndex deckIndexRepository;

    public abstract DeckArchetype getDeckArchetype(DeckArchetypeEntity deckArchetypeEntity);

    @AfterMapping
    protected void afterMapping(@MappingTarget DeckArchetype deckArchetype, DeckArchetypeEntity entity) {
        deckArchetype.setDeckCount(deckCount(DeckQuery.builder().archetype(entity.getId()).build()));
        deckArchetype.setTournamentCount(deckCount(DeckQuery.builder().archetype(entity.getId()).type(DeckType.TOURNAMENT).build()));
        deckArchetype.setTournament90Count(deckCount(DeckQuery.builder().archetype(entity.getId()).type(DeckType.TOURNAMENT).creationDate(LocalDate.now().minusDays(90)).build()));
        deckArchetype.setTournament180Count(deckCount(DeckQuery.builder().archetype(entity.getId()).type(DeckType.TOURNAMENT).creationDate(LocalDate.now().minusDays(180)).build()));
        deckArchetype.setTournament365Count(deckCount(DeckQuery.builder().archetype(entity.getId()).type(DeckType.TOURNAMENT).creationDate(LocalDate.now().minusDays(365)).build()));
        if (entity.getDeckId() != null) {
            Deck deck = deckIndexRepository.get(entity.getDeckId());
            if (deck != null && deck.getStats() != null) {
                deckArchetype.setPrice(deck.getStats().getPrice());
                deckArchetype.setCurrency(deck.getStats().getCurrency());
            }
        }
    }

    private long deckCount(DeckQuery query) {
        try (ResultSet<Deck> deckResultSet = deckIndexRepository.selectAll(query)) {
            return deckResultSet.stream().count();
        }
    }
}
