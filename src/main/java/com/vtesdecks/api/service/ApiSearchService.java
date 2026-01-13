package com.vtesdecks.api.service;

import com.vtesdecks.api.mapper.ApiPublicUserMapper;
import com.vtesdecks.api.util.ApiUtils;
import com.vtesdecks.jpa.repositories.UserRepository;
import com.vtesdecks.model.ApiDeckType;
import com.vtesdecks.model.DeckQuery;
import com.vtesdecks.model.DeckSort;
import com.vtesdecks.model.api.ApiDeck;
import com.vtesdecks.model.api.ApiDecks;
import com.vtesdecks.model.api.ApiPublicUser;
import com.vtesdecks.model.api.ApiSearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiSearchService {
    private static final int LIMIT = 5;
    private final UserRepository userRepository;
    private final ApiPublicUserMapper apiPublicUserMapper;
    private final ApiCardService apiCardService;
    private final ApiDeckService deckService;

    public ApiSearchResponse search(String query, String currencyCode) {
        ApiSearchResponse response = new ApiSearchResponse();
        response.setCards(getSearchCards(query));
        response.setDecks(getSearchDecks(query, currencyCode));
        response.setUsers(getSearchUsers(query));
        return response;
    }

    private List<Object> getSearchCards(String query) {
        return apiCardService.searchCards(query, null, LIMIT, Set.of("id", "name"));
    }

    private List<ApiDeck> getSearchDecks(String query, String currencyCode) {
        DeckQuery preconstructedDeckQuery = DeckQuery.builder()
                .apiType(ApiDeckType.PRECONSTRUCTED)
                .order(DeckSort.NEWEST)
                .userId(ApiUtils.extractUserId())
                .name(query)
                .build();
        ApiDecks result = deckService.getDecks(preconstructedDeckQuery, null, null, currencyCode, 0, LIMIT);
        List<ApiDeck> decks = new ArrayList<>(result.getDecks());
        if (decks.size() < 5) {
            DeckQuery deckQuery = DeckQuery.builder()
                    .apiType(ApiDeckType.ALL)
                    .order(DeckSort.POPULAR)
                    .userId(ApiUtils.extractUserId())
                    .name(query)
                    .build();
            result = deckService.getDecks(deckQuery, null, null, currencyCode, 0, LIMIT - decks.size());
            decks.addAll(result.getDecks());
        }
        return decks;
    }


    private List<ApiPublicUser> getSearchUsers(String query) {
        return userRepository.findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(query, query)
                .stream()
                .limit(LIMIT)
                .map(userEntity -> apiPublicUserMapper.mapPublicUser(userEntity, null))
                .toList();
    }


}
