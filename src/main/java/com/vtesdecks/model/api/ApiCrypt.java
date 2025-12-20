package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiCrypt {
    private Integer id;
    private String name;
    private String aka;
    private String type;
    private String clan;
    private String path;
    private Boolean adv;
    private Integer group;
    private Integer capacity;
    private String text;
    private List<String> sets;
    private String title;
    private String banned;
    private String artist;
    private String image;
    private String cropImage;
    private String clanIcon;
    private String pathIcon;
    private Set<String> disciplines;
    private Set<String> superiorDisciplines;
    private Set<String> disciplineIcons;
    private String sect;
    private Set<String> taints;
    private Long deckPopularity;
    private Long cardPopularity;
    private ApiI18n i18n;
    private Boolean printOnDemand;
    private Boolean unreleased;
    private BigDecimal minPrice;
    private Double score;
    private LocalDateTime lastUpdate;
}
