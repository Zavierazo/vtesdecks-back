package com.vtesdecks.model.api;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiClanStat {
    private Set<String> clans;
    private Integer number;
}
