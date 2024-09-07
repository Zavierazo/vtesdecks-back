package com.vtesdecks.service;

import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.model.DeckSort;
import com.vtesdecks.model.DeckType;

import java.util.List;

public interface DeckService {

    Deck getDeck(String deckId);

    ResultSet<Deck> getDecks(DeckType type,
                             DeckSort order,
                             Integer userId,
                             String name,
                             String author,
                             String cardText,
                             List<String> clans,
                             List<String> disciplines,
                             List<String> cards,
                             List<Integer> cryptSize,
                             List<Integer> librarySize,
                             List<Integer> group,
                             Boolean starVampire,
                             Boolean singleClan,
                             Boolean singleDiscipline,
                             List<Integer> year,
                             List<Integer> players,
                             String master,
                             String action,
                             String political,
                             String retainer,
                             String equipment,
                             String ally,
                             String modifier,
                             String combat,
                             String reaction,
                             String event,
                             Boolean absoluteProportion,
                             List<String> tags,
                             Boolean favorite);
}
