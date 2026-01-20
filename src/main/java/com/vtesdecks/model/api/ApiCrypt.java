package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiCrypt extends ApiBaseCard {
    private String type;
    private String clan;
    private String path;
    private Boolean adv;
    private Integer group;
    private Integer capacity;
    private List<String> sets;
    private String title;
    private String image;
    private String cropImage;
    private String clanIcon;
    private String pathIcon;
    private Set<String> disciplines;
    private Set<String> superiorDisciplines;
    private String sect;
    private BigDecimal minPrice;
    private Set<Integer> limitedFormats;
}
