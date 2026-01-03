package com.vtesdecks.service;

import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.model.DeckQuery;


public interface DeckService {

    Deck getDeck(String deckId);

    ResultSet<Deck> getDecks(DeckQuery deckQuery);
}
