package com.vtesdecks.api.mapper;

import com.vtesdecks.api.service.ApiCollectionService;
import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.indexable.Crypt;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.cache.indexable.Library;
import com.vtesdecks.cache.indexable.deck.CollectionTracker;
import com.vtesdecks.cache.indexable.deck.card.Card;
import com.vtesdecks.jpa.entity.DeckUserEntity;
import com.vtesdecks.jpa.repositories.DeckUserRepository;
import com.vtesdecks.model.Errata;
import com.vtesdecks.model.api.ApiCard;
import com.vtesdecks.model.api.ApiDeck;
import com.vtesdecks.model.api.ApiDeckStats;
import com.vtesdecks.model.api.ApiErrata;
import com.vtesdecks.service.CurrencyExchangeService;
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
import java.util.Optional;

import static com.vtesdecks.util.Constants.DEFAULT_CURRENCY;
import static com.vtesdecks.util.VtesUtils.isCrypt;

@Mapper(componentModel = "spring", uses = {ApiPublicUserMapper.class})
public abstract class ApiDeckMapper {

    @Autowired
    private DeckUserRepository deckUserRepository;
    @Autowired
    private LibraryCache libraryCache;
    @Autowired
    private CryptCache cryptCache;
    @Autowired
    private ApiCollectionService apiCollectionService;
    @Autowired
    private CurrencyExchangeService currencyExchangeService;


    @BeanMapping(qualifiedByName = "map")
    @Mapping(target = "user", source = "deck", qualifiedByName = "mapDeckUser")
    public abstract ApiDeck map(Deck deck, @Context Integer userId, @Context boolean collectionTracker, @Context String currencyCode);

    @BeanMapping(qualifiedByName = "mapSummary")
    @Mapping(target = "url", ignore = true)
    @Mapping(target = "source", ignore = true)
    @Mapping(target = "description", ignore = true)
    @Mapping(target = "crypt", ignore = true)
    @Mapping(target = "stats.cryptDisciplines", ignore = true)
    @Mapping(target = "stats.libraryDisciplines", ignore = true)
    @Mapping(target = "stats.libraryClans", ignore = true)
    @Mapping(target = "erratas", ignore = true)
    @Mapping(target = "warnings", ignore = true)
    @Mapping(target = "extra", ignore = true)
    @Mapping(target = "user", source = "deck", qualifiedByName = "mapDeckUser")
    public abstract ApiDeck mapSummary(Deck deck, @Context Integer userId, @Context Map<Integer, Integer> cardsFilter, @Context String currencyCode);

    @Named("map")
    @AfterMapping
    protected void afterMapping(@MappingTarget ApiDeck api, Deck deck, @Context Integer userId, @Context boolean collectionTracker, @Context String currencyCode) {
        if (deck.getLibraryByType() != null) {
            List<ApiCard> cardList = new ArrayList<>();
            for (Map.Entry<String, List<Card>> deckEntry : deck.getLibraryByType().entrySet()) {
                cardList.addAll(map(deckEntry.getValue()));
            }
            api.setLibrary(cardList);
        }
        if (userId != null) {
            afterMappingUser(api, userId, deck);
            Optional<DeckUserEntity> deckUser = deckUserRepository.findById(new DeckUserEntity.DeckUserId(userId, deck.getId()));
            deckUser.ifPresent(deckUserEntity -> api.setRated(deckUserEntity.getRate() != null));
        }
        if (collectionTracker || (Boolean.TRUE.equals(api.getOwner()) && Boolean.TRUE.equals(api.getCollection()))) {
            Map<Integer, Integer> collectionMap = apiCollectionService.getCollectionCardsMap();
            for (ApiCard apiCard : api.getCrypt()) {
                fillCollectionTracker(apiCard, collectionMap);
            }
            for (ApiCard apiCard : api.getLibrary()) {
                fillCollectionTracker(apiCard, collectionMap);
            }
        }
        convertPriceCurrency(api.getStats(), currencyCode);
    }

    private static void fillCollectionTracker(ApiCard apiCard, Map<Integer, Integer> collectionMap) {
        Integer number = collectionMap.get(apiCard.getId());
        if (number != null && apiCard.getNumber() != null) {
            if (apiCard.getNumber() > number) {
                apiCard.setCollection(CollectionTracker.PARTIAL);
            } else {
                apiCard.setCollection(CollectionTracker.FULL);
            }
        } else {
            apiCard.setCollection(CollectionTracker.NONE);
        }
    }

    @Named("mapSummary")
    @AfterMapping
    protected void afterMappingSummary(@MappingTarget ApiDeck api, Deck deck, @Context Integer userId, @Context Map<Integer, Integer> cardsFilter, @Context String currencyCode) {
        if (userId != null) {
            afterMappingUser(api, userId, deck);
        }
        if (deck.getExtra() != null && deck.getExtra().has("advent")) {
            api.setExtra(deck.getExtra());
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
        convertPriceCurrency(api.getStats(), currencyCode);
    }

    private void afterMappingUser(ApiDeck api, Integer userId, Deck deck) {
        api.setOwner(Objects.equals(userId, deck.getUser() != null ? deck.getUser().getId() : null));
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

    private void convertPriceCurrency(ApiDeckStats stats, String currencyCode) {
        if (stats.getPrice() != null && currencyCode != null && !currencyCode.equalsIgnoreCase(DEFAULT_CURRENCY)) {
            stats.setPrice(currencyExchangeService.convert(stats.getPrice(), DEFAULT_CURRENCY, currencyCode));
            stats.setCurrency(currencyCode);
        }
    }
}
