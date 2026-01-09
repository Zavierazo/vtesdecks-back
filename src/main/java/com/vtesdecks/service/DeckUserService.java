package com.vtesdecks.service;

public interface DeckUserService {

    void rate(Integer userId, String deckId, Integer rate);

    Boolean favorite(Integer userId, String deckId, Boolean favorite);

    void refreshUserDecks(Integer userId);
}
