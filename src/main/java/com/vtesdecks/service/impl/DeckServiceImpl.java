package com.vtesdecks.service.impl;

import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.cache.DeckIndex;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.model.DeckQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeckServiceImpl implements com.vtesdecks.service.DeckService {
    private final DeckIndex deckIndex;

    @Override
    public Deck getDeck(String deckId) {
        return deckIndex.get(deckId);
    }

    @Override
    public ResultSet<Deck> getDecks(DeckQuery deckQuery) {
        return deckIndex.selectAll(deckQuery);
    }
}
