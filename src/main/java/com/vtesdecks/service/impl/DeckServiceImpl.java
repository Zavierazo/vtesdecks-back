package com.vtesdecks.service.impl;

import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.cache.DeckIndex;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.cache.indexable.DeckSummary;
import com.vtesdecks.model.DeckQuery;
import com.vtesdecks.service.DeckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeckServiceImpl implements DeckService {
    private final DeckIndex deckIndex;

    @Override
    public DeckSummary getSummary(String deckId) {
        return deckIndex.get(deckId);
    }

    @Override
    public Deck getDeck(String deckId) {
        return deckIndex.getFull(deckId);
    }

    @Override
    public ResultSet<DeckSummary> getDecks(DeckQuery deckQuery) {
        return deckIndex.selectAll(deckQuery);
    }
}
