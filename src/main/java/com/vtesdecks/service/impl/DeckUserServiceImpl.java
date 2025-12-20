package com.vtesdecks.service.impl;

import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.cache.DeckIndex;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.jpa.entity.DeckUserEntity;
import com.vtesdecks.jpa.repositories.DeckUserRepository;
import com.vtesdecks.messaging.MessageProducer;
import com.vtesdecks.model.DeckQuery;
import com.vtesdecks.model.DeckSort;
import com.vtesdecks.service.DeckUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeckUserServiceImpl implements DeckUserService {
    private final DeckUserRepository deckUserRepository;
    private final DeckIndex deckIndex;
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
    public List<String> getFavoriteDecks(Integer userId) {
        List<DeckUserEntity> deckUsers = deckUserRepository.findFavoriteTrueByIdUserOrderByModificationDateDesc(userId);
        if (CollectionUtils.isNotEmpty(deckUsers)) {
            return deckUsers.stream().map(deckUserEntity -> deckUserEntity.getId().getDeckId()).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @Override
    public void refreshUserDecks(Integer userId) {
        ResultSet<Deck> deckUsers = deckIndex.selectAll(DeckQuery
                .builder()
                .type(DeckType.USER)
                .order(DeckSort.NEWEST)
                .user(userId)
                .build());
        if (deckUsers != null) {
            deckUsers.forEach(deck -> messageProducer.publishDeckSync(deck.getId()));
        }
    }

    @Override
    @Deprecated
    public List<String> getUserDecks(Integer userId) {
        ResultSet<Deck> deckUsers = deckIndex.selectAll(DeckQuery
                .builder()
                .type(DeckType.USER)
                .order(DeckSort.NEWEST)
                .user(userId)
                .build());
        if (deckUsers != null && deckUsers.isNotEmpty()) {
            return deckUsers.stream().map(Deck::getId).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

}
