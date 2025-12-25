package com.vtesdecks.api.service;

import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.indexable.Card;
import com.vtesdecks.cache.indexable.Crypt;
import com.vtesdecks.cache.indexable.Library;
import com.vtesdecks.model.api.ApiCollectionStats;
import com.vtesdecks.model.api.CollectionSectionStats;
import com.vtesdecks.service.CurrencyExchangeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

import static com.vtesdecks.util.Constants.DEFAULT_CURRENCY;

@Service
@RequiredArgsConstructor
public class ApiCollectionStatsService {
    private final ApiCollectionService collectionService;
    private final CurrencyExchangeService currencyExchangeService;
    private final CryptCache cryptCache;
    private final LibraryCache libraryCache;


    public ApiCollectionStats getCollectionStats(String currencyCode) {
        Map<Integer, Integer> collection = collectionService.getCollectionCardsMap();
        ApiCollectionStats apiCollectionStats = new ApiCollectionStats();
        apiCollectionStats.setCurrency(currencyCode);
        try (ResultSet<Crypt> cryptResultSet = cryptCache.selectAll(null, null)) {
            for (Crypt crypt : cryptResultSet) {
                int number = collection.getOrDefault(crypt.getId(), 0);
                increaseStats(apiCollectionStats.getOverall(), currencyCode, crypt, number);
                increaseStats(apiCollectionStats.getCrypt(), currencyCode, crypt, number);
                CollectionSectionStats clanStats = apiCollectionStats.getClans().computeIfAbsent(crypt.getClan(), k -> new CollectionSectionStats());
                increaseStats(clanStats, currencyCode, crypt, number);
                for (String fullSet : crypt.getSets()) {
                    String set = fullSet.split(":")[0];
                    CollectionSectionStats setStats = apiCollectionStats.getSets().computeIfAbsent(set, k -> new CollectionSectionStats());
                    increaseStats(setStats, currencyCode, crypt, number);
                }
            }
        }
        try (ResultSet<Library> libraryResultSet = libraryCache.selectAll(null, null)) {
            for (Library library : libraryResultSet) {
                int number = collection.getOrDefault(library.getId(), 0);
                increaseStats(apiCollectionStats.getOverall(), currencyCode, library, number);
                increaseStats(apiCollectionStats.getLibrary(), currencyCode, library, number);
                for (String type : library.getTypes()) {
                    CollectionSectionStats typeStats = apiCollectionStats.getTypes().computeIfAbsent(type, k -> new CollectionSectionStats());
                    increaseStats(typeStats, currencyCode, library, number);
                }
                for (String fullSet : library.getSets()) {
                    String set = fullSet.split(":")[0];
                    CollectionSectionStats setStats = apiCollectionStats.getSets().computeIfAbsent(set, k -> new CollectionSectionStats());
                    increaseStats(setStats, currencyCode, library, number);
                }
            }
        }


        return apiCollectionStats;
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


}
