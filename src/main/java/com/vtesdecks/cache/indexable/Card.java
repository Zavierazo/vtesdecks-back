package com.vtesdecks.cache.indexable;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Data
public class Card {
    private Map<String, I18n> i18n;
    private String image;
    private String cropImage;
    private Set<String> taints;
    private Long deckPopularity;
    private Long cardPopularity;
    private boolean printOnDemand;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private LocalDateTime lastUpdate;
}
