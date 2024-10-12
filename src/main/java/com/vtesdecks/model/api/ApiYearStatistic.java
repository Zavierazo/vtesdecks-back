package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiYearStatistic {
    private List<ApiStatistic> tags;
    private List<ApiStatistic> clans;
    private List<ApiStatistic> disciplines;
}
