package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiBaseCard {
    private Integer id;
    private String name;
    private String aka;
    private String text;
    private String banned;
    private String artist;
    private Set<String> disciplineIcons;
    private String sect;
    private Set<String> taints;
    private Long deckPopularity;
    private Long cardPopularity;
    private ApiI18n i18n;
    private Boolean printOnDemand;
    private Boolean unreleased;
    private Double score;
    private LocalDateTime lastUpdate;
}
