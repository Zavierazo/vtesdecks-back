package com.vtesdecks.cache.indexable.deck;

import lombok.Data;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Data
public class Stats {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private int crypt = 0;
    private int library = 0;
    private int event = 0;
    private int master = 0;
    private int action = 0;
    private int politicalAction = 0;
    private int equipment = 0;
    private int retainer = 0;
    private int ally = 0;
    private int actionModifier = 0;
    private int combat = 0;
    private int reaction = 0;
    private int masterTrifle = 0;
    private int poolCost = 0;
    private int bloodCost = 0;
    private BigDecimal avgCrypt;
    private int minCrypt;
    private int maxCrypt;
    private BigDecimal price;
    private BigDecimal msrp;
    private String currency;
    private List<DisciplineStat> cryptDisciplines = new ArrayList<>();
    private List<DisciplineStat> libraryDisciplines = new ArrayList<>();
    private List<ClanStat> libraryClans = new ArrayList<>();

    public Integer getPercentage(Integer count) {
        if (library == 0 || count == 0) {
            return 0;
        }
        BigDecimal bigCount = new BigDecimal(BigInteger.valueOf(count), 2);
        BigDecimal libCount = new BigDecimal(BigInteger.valueOf(library), 2);
        return bigCount.divide(libCount, RoundingMode.HALF_UP).multiply(HUNDRED).intValue();
    }
}
