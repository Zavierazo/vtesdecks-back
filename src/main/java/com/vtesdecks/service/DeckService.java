package com.vtesdecks.service;

import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.cache.indexable.DeckSummary;
import com.vtesdecks.model.DeckQuery;


public interface DeckService {

    DeckSummary getSummary(String deckId);

    Deck getDeck(String deckId);

    ResultSet<DeckSummary> getDecks(DeckQuery deckQuery);
}
