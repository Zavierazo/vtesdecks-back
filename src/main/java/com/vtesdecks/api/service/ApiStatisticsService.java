package com.vtesdecks.api.service;

import com.google.common.collect.Lists;
import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.api.util.ApiUtils;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.model.DeckSort;
import com.vtesdecks.model.DeckTag;
import com.vtesdecks.model.DeckType;
import com.vtesdecks.model.api.ApiHistoricStatistic;
import com.vtesdecks.model.api.ApiStatistic;
import com.vtesdecks.model.api.ApiYearStatistic;
import com.vtesdecks.service.DeckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiStatisticsService {
    private static final List<String> TAGS_ORDER = Stream.of(DeckTag.values()).map(DeckTag::getTag).collect(Collectors.toList());
    private final DeckService deckService;

    public ApiYearStatistic getYearStatistic(DeckType type, Integer year) {
        ApiYearStatistic apiYearStatistic = new ApiYearStatistic();

        ResultSet<Deck> decks = getDecks(type, year);

        Map<String, ApiStatistic> tags = new HashMap<>();
        Map<String, ApiStatistic> clans = new HashMap<>();
        Map<String, ApiStatistic> disciplines = new HashMap<>();
        for (Deck deck : decks) {
            for (String tag : deck.getTags()) {
                ApiStatistic apiStatistic = tags.get(tag);
                if (apiStatistic == null) {
                    apiStatistic = new ApiStatistic();
                    apiStatistic.setLabel(tag);
                    tags.put(tag, apiStatistic);
                }
                apiStatistic.setCount(apiStatistic.getCount() + 1);
            }
            for (String clan : deck.getClans()) {
                ApiStatistic apiStatistic = clans.get(clan);
                if (apiStatistic == null) {
                    apiStatistic = new ApiStatistic();
                    apiStatistic.setLabel(clan);
                    clans.put(clan, apiStatistic);
                }
                apiStatistic.setCount(apiStatistic.getCount() + 1);
            }
            for (String discipline : deck.getDisciplines()) {
                ApiStatistic apiStatistic = disciplines.get(discipline);
                if (apiStatistic == null) {
                    apiStatistic = new ApiStatistic();
                    apiStatistic.setLabel(discipline);
                    disciplines.put(discipline, apiStatistic);
                }
                apiStatistic.setCount(apiStatistic.getCount() + 1);
            }
        }
        List<ApiStatistic> apiTagStatistics = Lists.newArrayList(tags.values());
        apiTagStatistics.sort(Comparator.comparingInt(o -> TAGS_ORDER.indexOf(o.getLabel())));
        apiTagStatistics.forEach(apiStatistic -> apiStatistic.setPercentage(getPercentage(apiStatistic.getCount(), decks.size())));
        apiYearStatistic.setTags(apiTagStatistics);

        List<ApiStatistic> apiClanStatistics = Lists.newArrayList(clans.values());
        apiClanStatistics.sort((o1, o2) -> o2.getCount().compareTo(o1.getCount()));
        apiClanStatistics.forEach(apiStatistic -> apiStatistic.setPercentage(getPercentage(apiStatistic.getCount(), decks.size())));
        apiYearStatistic.setClans(apiClanStatistics);

        List<ApiStatistic> apiDisciplineStatistics = Lists.newArrayList(disciplines.values());
        apiDisciplineStatistics.sort((o1, o2) -> o2.getCount().compareTo(o1.getCount()));
        apiDisciplineStatistics.forEach(apiStatistic -> apiStatistic.setPercentage(getPercentage(apiStatistic.getCount(), decks.size())));
        apiYearStatistic.setDisciplines(apiDisciplineStatistics);

        return apiYearStatistic;
    }


    public List<ApiHistoricStatistic> getHistoricTagStatistic(DeckType type) {
        List<ApiHistoricStatistic> apiHistoricStatistic = new ArrayList<>();
        ResultSet<Deck> decks = getDecks(type, null);
        Map<String, Map<Integer, ApiStatistic>> years = new HashMap<>();
        Map<Integer, Integer> deckCount = new HashMap<>();
        for (Deck deck : decks) {
            for (String tag : deck.getTags()) {
                Map<Integer, ApiStatistic> yearStatistics = years.getOrDefault(tag, new HashMap<>());
                ApiStatistic apiStatistic = yearStatistics.get(deck.getYear());
                if (apiStatistic == null) {
                    apiStatistic = new ApiStatistic();
                    apiStatistic.setLabel(deck.getYear().toString());
                    yearStatistics.put(deck.getYear(), apiStatistic);
                }
                apiStatistic.setCount(apiStatistic.getCount() + 1);
                years.put(tag, yearStatistics);
            }
            deckCount.put(deck.getYear(), deckCount.getOrDefault(deck.getYear(), 0) + 1);
        }
        years.forEach((tag, yearStatistics) -> {
            List<ApiStatistic> apiStatisticList = new ArrayList<>(yearStatistics.values());
            apiStatisticList.sort(Comparator.comparingInt(o -> Integer.parseInt(o.getLabel())));
            apiStatisticList.forEach(apiStatistic -> apiStatistic.setPercentage(getPercentage(apiStatistic.getCount(), deckCount.getOrDefault(Integer.parseInt(apiStatistic.getLabel()), 0))));
            apiHistoricStatistic.add(new ApiHistoricStatistic(tag, apiStatisticList));
        });
        return apiHistoricStatistic;
    }

    public List<ApiHistoricStatistic> getHistoricClanStatistic(DeckType type) {
        List<ApiHistoricStatistic> apiHistoricStatistic = new ArrayList<>();
        ResultSet<Deck> decks = getDecks(type, null);
        Map<String, Map<Integer, ApiStatistic>> years = new HashMap<>();
        Map<Integer, Integer> deckCount = new HashMap<>();
        for (Deck deck : decks) {
            for (String clan : deck.getClans()) {
                Map<Integer, ApiStatistic> yearStatistics = years.getOrDefault(clan, new HashMap<>());
                ApiStatistic apiStatistic = yearStatistics.get(deck.getYear());
                if (apiStatistic == null) {
                    apiStatistic = new ApiStatistic();
                    apiStatistic.setLabel(deck.getYear().toString());
                    yearStatistics.put(deck.getYear(), apiStatistic);
                }
                apiStatistic.setCount(apiStatistic.getCount() + 1);
                years.put(clan, yearStatistics);
            }
            deckCount.put(deck.getYear(), deckCount.getOrDefault(deck.getYear(), 0) + 1);
        }
        years.forEach((clan, yearStatistics) -> {
            List<ApiStatistic> apiStatisticList = new ArrayList<>(yearStatistics.values());
            apiStatisticList.sort(Comparator.comparingInt(o -> Integer.parseInt(o.getLabel())));
            apiStatisticList.forEach(apiStatistic -> apiStatistic.setPercentage(getPercentage(apiStatistic.getCount(), deckCount.getOrDefault(Integer.parseInt(apiStatistic.getLabel()), 0))));
            apiHistoricStatistic.add(new ApiHistoricStatistic(clan, apiStatisticList));
        });
        return apiHistoricStatistic;
    }

    public List<ApiHistoricStatistic> getHistoricDisciplineStatistic(DeckType type) {
        List<ApiHistoricStatistic> apiHistoricStatistic = new ArrayList<>();
        ResultSet<Deck> decks = getDecks(type, null);
        Map<String, Map<Integer, ApiStatistic>> years = new HashMap<>();
        Map<Integer, Integer> deckCount = new HashMap<>();
        for (Deck deck : decks) {
            for (String discipline : deck.getDisciplines()) {
                Map<Integer, ApiStatistic> yearStatistics = years.getOrDefault(discipline, new HashMap<>());
                ApiStatistic apiStatistic = yearStatistics.get(deck.getYear());
                if (apiStatistic == null) {
                    apiStatistic = new ApiStatistic();
                    apiStatistic.setLabel(deck.getYear().toString());
                    yearStatistics.put(deck.getYear(), apiStatistic);
                }
                apiStatistic.setCount(apiStatistic.getCount() + 1);
                years.put(discipline, yearStatistics);
            }
            deckCount.put(deck.getYear(), deckCount.getOrDefault(deck.getYear(), 0) + 1);
        }
        years.forEach((discipline, yearStatistics) -> {
            List<ApiStatistic> apiStatisticList = new ArrayList<>(yearStatistics.values());
            apiStatisticList.sort(Comparator.comparingInt(o -> Integer.parseInt(o.getLabel())));
            apiStatisticList.forEach(apiStatistic -> apiStatistic.setPercentage(getPercentage(apiStatistic.getCount(), deckCount.getOrDefault(Integer.parseInt(apiStatistic.getLabel()), 0))));
            apiHistoricStatistic.add(new ApiHistoricStatistic(discipline, apiStatisticList));
        });
        return apiHistoricStatistic;
    }

    private ResultSet<Deck> getDecks(DeckType type, Integer year) {
        return deckService.getDecks(type, DeckSort.NEWEST, ApiUtils.extractUserId(), null, null, null,
                null, null, null, null, null, null, null,
                null, null, year != null ? Lists.newArrayList(year, year) : null, null, null, null, null,
                null, null, null, null, null, null, null,
                null, null, null);
    }

    private static BigDecimal getPercentage(Integer count, int size) {
        if (size == 0 || count == 0) {
            return BigDecimal.ZERO;
        }
        MathContext mathContext = new MathContext(10, RoundingMode.HALF_UP);
        BigDecimal countBigDecimal = BigDecimal.valueOf(count);
        BigDecimal sizeBigDecimal = BigDecimal.valueOf(size);
        return countBigDecimal.divide(sizeBigDecimal, mathContext)
                .multiply(new BigDecimal("100.0"), mathContext)
                .setScale(2, RoundingMode.HALF_UP);
    }

}
