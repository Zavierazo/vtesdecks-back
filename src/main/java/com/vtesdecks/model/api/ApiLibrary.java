package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiLibrary {
    private Integer id;
    private String name;
    private String aka;
    private String type;
    private Set<String> clans;
    private Set<String> clanIcons;
    private Integer poolCost;
    private Integer bloodCost;
    private Integer convictionCost;
    private Boolean burn;
    private String text;
    private String flavor;
    private List<String> sets;
    private String requirement;
    private String banned;
    private String artist;
    private String capacity;
    private String image;
    private String cropImage;
    private Boolean trifle;
    private Set<String> disciplines;
    private Set<String> types;
    private Set<String> typeIcons;
    private Set<String> disciplineIcons;
    private Set<String> sects;
    private Set<String> titles;
    private Set<String> taints;
    private Long deckPopularity;
    private Long cardPopularity;
    private ApiI18n i18n;
    private boolean printOnDemand;
    private LocalDateTime lastUpdate;
}
