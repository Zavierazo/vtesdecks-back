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
public class ApiLibrary extends ApiBaseCard {
    private String type;
    private Set<String> clans;
    private String path;
    private Integer poolCost;
    private Integer bloodCost;
    private Integer convictionCost;
    private Boolean burn;
    private String flavor;
    private List<String> sets;
    private String requirement;
    private String capacity;
    private String image;
    private Set<String> typeIcons;
    private Set<String> clanIcons;
    private String pathIcon;
    private Set<String> sects;
    private Set<String> titles;
    private BigDecimal minPrice;
    private Set<Integer> limitedFormats;
}
