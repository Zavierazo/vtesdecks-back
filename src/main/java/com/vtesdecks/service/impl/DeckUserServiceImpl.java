package com.vtesdecks.service.impl;

import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.cache.DeckIndex;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.db.DeckUserMapper;
import com.vtesdecks.db.model.DbDeckUser;
import com.vtesdecks.model.DeckQuery;
import com.vtesdecks.model.DeckSort;
import com.vtesdecks.service.DeckUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DeckUserServiceImpl implements DeckUserService {
    @Autowired
    private DeckUserMapper deckUserMapper;
    @Autowired
    private DeckIndex deckIndex;

    @Override
    public void rate(Integer userId, String deckId, Integer rate) {
        if (rate != null && userId != null && deckId != null) {
            DbDeckUser deckUser = deckUserMapper.selectById(userId, deckId);
            boolean updated = false;
            if (deckUser == null) {
                deckUser = new DbDeckUser();
                deckUser.setUser(userId);
                deckUser.setDeckId(deckId);
                deckUser.setRate(rate);
                deckUserMapper.insert(deckUser);
                updated = true;
            } else if (!rate.equals(deckUser.getRate())) {
                deckUser.setRate(rate);
                deckUserMapper.update(deckUser);
                updated = true;
            }
            if (updated) {
                deckIndex.enqueueRefreshIndex(deckId);
            }
            log.debug("Deck rate {}", deckUser);
        }
    }

    @Override
    public Boolean favorite(Integer userId, String deckId, Boolean favorite) {
        if (userId != null && deckId != null && favorite != null) {
            DbDeckUser deckUser = deckUserMapper.selectById(userId, deckId);
            log.debug("Update favorite for {} {} to {}", userId, deckId, favorite);
            boolean updated = false;
            if (deckUser == null) {
                deckUser = new DbDeckUser();
                deckUser.setUser(userId);
                deckUser.setDeckId(deckId);
                deckUser.setFavorite(favorite);
                deckUserMapper.insert(deckUser);
                updated = true;
            } else if (favorite != deckUser.isFavorite()) {
                deckUser.setFavorite(favorite);
                deckUserMapper.update(deckUser);
                updated = true;
            }
            if (updated) {
                deckIndex.enqueueRefreshIndex(deckId);
            }
            return favorite;
        }
        return false;
    }

    @Override
    public List<String> getFavoriteDecks(Integer userId) {
        List<DbDeckUser> deckUsers = deckUserMapper.selectFavoriteByUser(userId);
        if (CollectionUtils.isNotEmpty(deckUsers)) {
            return deckUsers.stream().map(DbDeckUser::getDeckId).collect(Collectors.toList());
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
            deckUsers.forEach(deck -> deckIndex.enqueueRefreshIndex(deck.getId()));
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
