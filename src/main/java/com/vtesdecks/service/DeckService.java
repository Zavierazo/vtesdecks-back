package com.vtesdecks.service;

import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.model.DeckSort;
import com.vtesdecks.model.DeckType;

import java.util.List;
import java.util.Map;

public interface DeckService {

    Deck getDeck(String deckId);

    ResultSet<Deck> getDecks(DeckType type,
                             DeckSort order,
                             Integer userId,
                             String name,
                             String author,
                             Boolean exactAuthor,
                             String cardText,
                             List<String> clans,
                             List<String> disciplines,
                             Map<Integer, Integer> cards,
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
                             Integer archetype,
                             Boolean absoluteProportion,
                             List<String> tags,
                             String limitedFormat,
                             List<String> paths,
                             Boolean favorite);
}
