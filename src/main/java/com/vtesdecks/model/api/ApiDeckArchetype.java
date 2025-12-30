package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiDeckArchetype {
    private Integer id;
    private String name;
    private String icon;
    private String type;
    private String description;
    private String deckId;
    private Boolean enabled;
    private Long deckCount;
    private Long metaCount;
    private Long metaTotal;
    private BigDecimal price;
    private String currency;
    private LocalDateTime creationDate;
    private LocalDateTime modificationDate;
}

