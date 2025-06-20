package com.vtesdecks.api.mapper;

import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.indexable.Crypt;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.cache.indexable.Library;
import com.vtesdecks.cache.indexable.deck.card.Card;
import com.vtesdecks.db.DeckUserMapper;
import com.vtesdecks.db.model.DbDeckUser;
import com.vtesdecks.model.Errata;
import com.vtesdecks.model.api.ApiCard;
import com.vtesdecks.model.api.ApiDeck;
import com.vtesdecks.model.api.ApiErrata;
import com.vtesdecks.util.VtesUtils;
import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.vtesdecks.util.VtesUtils.isCrypt;

@Mapper(componentModel = "spring")
public abstract class ApiDeckMapper {

    @Autowired
    private DeckUserMapper deckUserMapper;
    @Autowired
    private LibraryCache libraryCache;
    @Autowired
    private CryptCache cryptCache;


    @BeanMapping(qualifiedByName = "map")
    public abstract ApiDeck map(Deck deck, @Context Integer userId);

    @BeanMapping(qualifiedByName = "mapSummary")
    @Mapping(target = "url", ignore = true)
    @Mapping(target = "source", ignore = true)
    @Mapping(target = "description", ignore = true)
    @Mapping(target = "crypt", ignore = true)
    @Mapping(target = "stats.cryptDisciplines", ignore = true)
    @Mapping(target = "stats.libraryDisciplines", ignore = true)
    @Mapping(target = "stats.libraryClans", ignore = true)
    @Mapping(target = "erratas", ignore = true)
    @Mapping(target = "extra", ignore = true)
    public abstract ApiDeck mapSummary(Deck deck, @Context Integer userId, @Context Map<Integer, Integer> cardsFilter);

    @Named("map")
    @AfterMapping
    protected void afterMapping(@MappingTarget ApiDeck api, Deck deck, @Context Integer userId) {
        if (deck.getLibraryByType() != null) {
            List<ApiCard> cardList = new ArrayList<>();
            for (Map.Entry<String, List<Card>> deckEntry : deck.getLibraryByType().entrySet()) {
                cardList.addAll(map(deckEntry.getValue()));
            }
            api.setLibrary(cardList);
        }
        if (userId != null) {
            afterMappingUser(api, userId, deck);
            DbDeckUser deckUser = deckUserMapper.selectById(userId, deck.getId());
            if (deckUser != null) {
                api.setRated(deckUser.getRate() != null);
            }
        }
    }

    @Named("mapSummary")
    @AfterMapping
    protected void afterMappingSummary(@MappingTarget ApiDeck api, Deck deck, @Context Integer userId, @Context Map<Integer, Integer> cardsFilter) {
        if (userId != null) {
            afterMappingUser(api, userId, deck);
        }
        if (cardsFilter != null && !cardsFilter.isEmpty()) {
            // Add number of cards for filtered cards
            api.setFilterCards(cardsFilter.entrySet().stream()
                    .filter(entry -> entry.getValue() > 0)
                    .map(entry -> {
                        ApiCard apiCard = new ApiCard();
                        apiCard.setId(entry.getKey());
                        apiCard.setNumber(0);
                        if (isCrypt(entry.getKey())) {
                            deck.getCrypt().stream()
                                    .filter(card -> entry.getKey().equals(card.getId()))
                                    .findFirst()
                                    .ifPresent(card -> apiCard.setNumber(card.getNumber()));
                        } else {
                            deck.getLibraryByType().values().stream()
                                    .flatMap(List::stream)
                                    .filter(card -> entry.getKey().equals(card.getId()))
                                    .findFirst()
                                    .ifPresent(card -> apiCard.setNumber(card.getNumber()));
                        }
                        return apiCard;
                    }).toList());
        }
    }

    private void afterMappingUser(ApiDeck api, Integer userId, Deck deck) {
        api.setOwner(Objects.equals(userId, deck.getUser()));
        api.setFavorite(deck.getFavoriteUsers() != null && deck.getFavoriteUsers().contains(userId));
    }

    protected abstract List<ApiCard> map(List<Card> card);

    @BeanMapping(qualifiedByName = "card")
    protected abstract ApiCard map(Card card);

    @Named("card")
    @AfterMapping
    protected void afterMapping(@MappingTarget ApiCard api) {
        if (VtesUtils.isLibrary(api.getId())) {
            Library library = libraryCache.get(api.getId());
            api.setType(library != null ? library.getType() : null);
        }
    }

    protected abstract ApiErrata map(Errata errata);

    @AfterMapping
    protected void afterMapping(@MappingTarget ApiErrata api) {
        if (VtesUtils.isLibrary(api.getId())) {
            Library library = libraryCache.get(api.getId());
            api.setName(library.getName());
        } else {
            Crypt crypt = cryptCache.get(api.getId());
            api.setName(crypt.getName());
        }
    }
}
