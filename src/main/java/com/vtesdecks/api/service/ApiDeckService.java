package com.vtesdecks.api.service;

import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.api.mapper.ApiDeckMapper;
import com.vtesdecks.api.util.ApiUtils;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.model.DeckSort;
import com.vtesdecks.model.DeckType;
import com.vtesdecks.model.api.ApiDeck;
import com.vtesdecks.model.api.ApiDecks;
import com.vtesdecks.service.DeckService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ApiDeckService {
    @Autowired
    private DeckService deckService;
    @Autowired
    private ApiDeckMapper mapper;

    public ApiDeck getDeck(String deckId, boolean detailed) {
        Deck deck = deckService.getDeck(deckId);
        if (deck == null) {
            return null;
        }
        if (detailed) {
            return mapper.map(deck, ApiUtils.extractUserId());
        } else {
            return mapper.mapSummary(deck, ApiUtils.extractUserId());
        }
    }

    public ApiDecks getDecks(DeckType type,
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
                             Boolean favorite,
                             Integer offset,
                             Integer limit) {
        ResultSet<Deck> decks = deckService.getDecks(type, order, userId, name, author, cardText, clans, disciplines,
                cards, cryptSize, librarySize, group, starVampire, singleClan, singleDiscipline, year, players, master, action, political, retainer,
                equipment, ally, modifier, combat, reaction, event, absoluteProportion, tags, favorite);
        ApiDecks apiDecks = new ApiDecks();
        apiDecks.setTotal(decks.size());
        apiDecks.setOffset(offset);
        apiDecks.setDecks(decks
                .stream()
                .skip(offset)
                .limit(limit)
                .map(deck -> mapper.mapSummary(deck, ApiUtils.extractUserId()))
                .collect(Collectors.toList()));
        return apiDecks;
    }
}
