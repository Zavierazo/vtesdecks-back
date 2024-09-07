package com.vtesdecks.model.api;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiDisciplineStat {
    private Set<String> disciplines;
    private Integer inferior;
    private Integer superior;
}
