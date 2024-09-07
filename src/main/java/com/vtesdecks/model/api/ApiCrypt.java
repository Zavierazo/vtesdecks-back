package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

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
    private Boolean adv;
    private Integer group;
    private Integer capacity;
    private List<String> sets;
    private String title;
    private String banned;
    private String artist;
    private String image;
    private String cropImage;
    private String clanIcon;
    private Set<String> disciplines;
    private Set<String> superiorDisciplines;
    private Set<String> disciplineIcons;
    private String sect;
    private Set<String> taints;
    private Long deckPopularity;
    private Long cardPopularity;
    private boolean printOnDemand;
    private LocalDateTime lastUpdate;
}
