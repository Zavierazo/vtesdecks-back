package com.vtesdecks.service.impl;

import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.jpa.entity.DeckUserEntity;
import com.vtesdecks.jpa.repositories.DeckUserRepository;
import com.vtesdecks.messaging.MessageProducer;
import com.vtesdecks.model.DeckQuery;
import com.vtesdecks.model.DeckSort;
import com.vtesdecks.service.DeckService;
import com.vtesdecks.service.DeckUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeckUserServiceImpl implements DeckUserService {
    private final DeckUserRepository deckUserRepository;
    private final DeckService deckService;
    private final MessageProducer messageProducer;

    @Override
    public void rate(Integer userId, String deckId, Integer rate) {
        if (rate != null && userId != null && deckId != null) {
            DeckUserEntity deckUser = deckUserRepository.findById(new DeckUserEntity.DeckUserId(userId, deckId)).orElse(null);
            boolean updated = false;
            if (deckUser == null) {
                deckUser = new DeckUserEntity();
                deckUser.setId(new DeckUserEntity.DeckUserId());
                deckUser.getId().setUser(userId);
                deckUser.getId().setDeckId(deckId);
                deckUser.setRate(rate);
                deckUser.setFavorite(false);
                deckUserRepository.save(deckUser);
                updated = true;
            } else if (!rate.equals(deckUser.getRate())) {
                deckUser.setRate(rate);
                deckUserRepository.save(deckUser);
                updated = true;
            }
            if (updated) {
                messageProducer.publishDeckSync(deckId);
            }
            log.debug("Deck rate {}", deckUser);
        }
    }

    @Override
    public Boolean favorite(Integer userId, String deckId, Boolean favorite) {
        if (userId != null && deckId != null && favorite != null) {
            DeckUserEntity deckUser = deckUserRepository.findById(new DeckUserEntity.DeckUserId(userId, deckId)).orElse(null);
            log.debug("Update favorite for {} {} to {}", userId, deckId, favorite);
            boolean updated = false;
            if (deckUser == null) {
                deckUser = new DeckUserEntity();
                deckUser.setId(new DeckUserEntity.DeckUserId());
                deckUser.getId().setUser(userId);
                deckUser.getId().setDeckId(deckId);
                deckUser.setFavorite(favorite);
                deckUserRepository.save(deckUser);
                updated = true;
            } else if (!favorite.equals(deckUser.getFavorite())) {
                deckUser.setFavorite(favorite);
                deckUserRepository.save(deckUser);
                updated = true;
            }
            if (updated) {
                messageProducer.publishDeckSync(deckId);
            }
            return favorite;
        }
        return false;
    }

    @Override
    public void refreshUserDecks(Integer userId) {
        ResultSet<Deck> deckUsers = deckService.getDecks(DeckQuery
                .builder()
                .type(DeckType.USER)
                .order(DeckSort.NEWEST)
                .userId(userId)
                .build());
        if (deckUsers != null) {
            deckUsers.forEach(deck -> messageProducer.publishDeckSync(deck.getId()));
        }
    }
}
