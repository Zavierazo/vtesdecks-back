package com.vtesdecks.cache.indexable;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
public class Card {
    private Integer id;
    private String name;
    private Set<String> nameTrigrams;
    private String aka;
    private Set<String> akaTrigrams;
    private Map<String, I18n> i18n;
    private String image;
    private String cropImage;
    private Set<String> taints;
    private List<String> sets;
    private Long deckPopularity;
    private Long cardPopularity;
    private boolean printOnDemand;
    private boolean unreleased;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Set<Integer> limitedFormats;
    private LocalDateTime lastUpdate;
}
