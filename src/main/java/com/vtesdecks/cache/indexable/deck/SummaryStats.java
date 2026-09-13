package com.vtesdecks.cache.indexable.deck;

import lombok.Data;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

@Data
public class SummaryStats {
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

    public Integer getPercentage(Integer count) {
        if (library == 0 || count == 0) {
            return 0;
        }
        BigDecimal bigCount = new BigDecimal(BigInteger.valueOf(count), 2);
        BigDecimal libCount = new BigDecimal(BigInteger.valueOf(library), 2);
        return bigCount.divide(libCount, RoundingMode.HALF_UP).multiply(HUNDRED).intValue();
    }

    public static SummaryStats from(SummaryStats stats) {
        if (stats == null) {
            return null;
        }
        SummaryStats summary = new SummaryStats();
        summary.setCrypt(stats.getCrypt());
        summary.setLibrary(stats.getLibrary());
        summary.setEvent(stats.getEvent());
        summary.setMaster(stats.getMaster());
        summary.setAction(stats.getAction());
        summary.setPoliticalAction(stats.getPoliticalAction());
        summary.setEquipment(stats.getEquipment());
        summary.setRetainer(stats.getRetainer());
        summary.setAlly(stats.getAlly());
        summary.setActionModifier(stats.getActionModifier());
        summary.setCombat(stats.getCombat());
        summary.setReaction(stats.getReaction());
        summary.setMasterTrifle(stats.getMasterTrifle());
        summary.setPoolCost(stats.getPoolCost());
        summary.setBloodCost(stats.getBloodCost());
        summary.setAvgCrypt(stats.getAvgCrypt());
        summary.setMinCrypt(stats.getMinCrypt());
        summary.setMaxCrypt(stats.getMaxCrypt());
        summary.setPrice(stats.getPrice());
        summary.setMsrp(stats.getMsrp());
        summary.setCurrency(stats.getCurrency());
        return summary;
    }
}
