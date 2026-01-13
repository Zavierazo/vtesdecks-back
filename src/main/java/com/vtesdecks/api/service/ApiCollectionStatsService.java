package com.vtesdecks.api.service;

import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.api.util.ApiUtils;
import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.indexable.Card;
import com.vtesdecks.cache.indexable.Crypt;
import com.vtesdecks.cache.indexable.Library;
import com.vtesdecks.jpa.entity.CollectionCardEntity;
import com.vtesdecks.jpa.entity.CollectionEntity;
import com.vtesdecks.jpa.repositories.CollectionCardRepository;
import com.vtesdecks.jpa.repositories.CollectionRepository;
import com.vtesdecks.model.api.ApiCollectionStats;
import com.vtesdecks.model.api.CollectionSectionStats;
import com.vtesdecks.service.CurrencyExchangeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.vtesdecks.util.Constants.DEFAULT_CURRENCY;

@Service
@RequiredArgsConstructor
public class ApiCollectionStatsService {
    private final CollectionRepository collectionRepository;
    private final CollectionCardRepository collectionCardRepository;
    private final CurrencyExchangeService currencyExchangeService;
    private final CryptCache cryptCache;
    private final LibraryCache libraryCache;


    public ApiCollectionStats getCollectionStats(String currencyCode) {
        Map<Integer, List<CollectionCardEntity>> collection = getCollectionCardsMap();
        ApiCollectionStats apiCollectionStats = new ApiCollectionStats();
        apiCollectionStats.setCurrency(currencyCode);
        try (ResultSet<Crypt> cryptResultSet = cryptCache.selectAll()) {
            for (Crypt crypt : cryptResultSet) {
                List<CollectionCardEntity> cards = collection.getOrDefault(crypt.getId(), Collections.emptyList());
                int number = cards.stream().mapToInt(CollectionCardEntity::getNumber).sum();
                increaseStats(apiCollectionStats.getCrypt(), currencyCode, crypt, number);
                CollectionSectionStats clanStats = apiCollectionStats.getClans().computeIfAbsent(crypt.getClan(), k -> new CollectionSectionStats());
                increaseStats(clanStats, currencyCode, crypt, number);
                fillCommon(currencyCode, crypt, apiCollectionStats, number, cards);
            }
        }
        try (ResultSet<Library> libraryResultSet = libraryCache.selectAll()) {
            for (Library library : libraryResultSet) {
                List<CollectionCardEntity> cards = collection.getOrDefault(library.getId(), Collections.emptyList());
                int number = cards.stream().mapToInt(CollectionCardEntity::getNumber).sum();
                increaseStats(apiCollectionStats.getLibrary(), currencyCode, library, number);
                for (String type : library.getTypes()) {
                    CollectionSectionStats typeStats = apiCollectionStats.getTypes().computeIfAbsent(type, k -> new CollectionSectionStats());
                    increaseStats(typeStats, currencyCode, library, number);
                }
                fillCommon(currencyCode, library, apiCollectionStats, number, cards);
            }
        }


        return apiCollectionStats;
    }

    private void fillCommon(String currencyCode, Card value, ApiCollectionStats apiCollectionStats, int number, List<CollectionCardEntity> cards) {
        increaseStats(apiCollectionStats.getOverall(), currencyCode, value, number);
        for (String fullSet : value.getSets()) {
            String set = fullSet.split(":")[0];
            int setNumber = cards.stream()
                    .filter(card -> Objects.equals(card.getSet(), set))
                    .mapToInt(CollectionCardEntity::getNumber)
                    .sum();
            CollectionSectionStats setStats = apiCollectionStats.getSets().computeIfAbsent(set, k -> new CollectionSectionStats());
            increaseStats(setStats, currencyCode, value, setNumber);
        }
    }

    private void increaseStats(CollectionSectionStats collectionStatsDetail, String currencyCode, Card card, int copies) {
        if (copies > 0) {
            collectionStatsDetail.setTotal(collectionStatsDetail.getTotal() + copies);
            collectionStatsDetail.getCollected().add(card.getId());
            if (card.getMinPrice() != null) {
                collectionStatsDetail.setPrice(collectionStatsDetail.getPrice().add(
                        currencyExchangeService.convert(card.getMinPrice(), DEFAULT_CURRENCY, currencyCode)
                                .multiply(BigDecimal.valueOf(copies))
                ));
            }
        } else {
            collectionStatsDetail.getMissing().add(card.getId());
        }
    }

    public Map<Integer, List<CollectionCardEntity>> getCollectionCardsMap() {
        Integer userId = ApiUtils.extractUserId();
        List<CollectionEntity> collectionEntity = collectionRepository.findByUserIdAndDeletedFalse(userId);
        if (collectionEntity != null && !collectionEntity.isEmpty()) {
            List<CollectionCardEntity> cards = collectionCardRepository.findByCollectionId(collectionEntity.getFirst().getId());
            return cards.stream().collect(Collectors.groupingBy(CollectionCardEntity::getCardId, Collectors.toList()));
        } else {
            return new HashMap<>();
        }
    }


}
