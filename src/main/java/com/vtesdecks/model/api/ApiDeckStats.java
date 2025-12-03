package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiDeckStats {
    private Integer crypt;
    private Integer library;
    private Integer event;
    private Integer master;
    private Integer action;
    private Integer politicalAction;
    private Integer equipment;
    private Integer retainer;
    private Integer ally;
    private Integer actionModifier;
    private Integer combat;
    private Integer reaction;
    private Integer masterTrifle;
    private Integer poolCost;
    private Integer bloodCost;
    private BigDecimal avgCrypt;
    private Integer minCrypt;
    private Integer maxCrypt;
    private BigDecimal price;
    private String currency;
    private List<ApiDisciplineStat> cryptDisciplines;
    private List<ApiDisciplineStat> libraryDisciplines;
    private List<ApiClanStat> libraryClans;
}
