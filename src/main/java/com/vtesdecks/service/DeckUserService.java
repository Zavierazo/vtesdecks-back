package com.vtesdecks.service;

import java.util.List;

public interface DeckUserService {

    void rate(Integer userId, String deckId, Integer rate);

    Boolean favorite(Integer userId, String deckId, Boolean favorite);

    List<String> getFavoriteDecks(Integer userId);

    @Deprecated
    List<String> getUserDecks(Integer userId);

    void refreshUserDecks(Integer userId);
}
