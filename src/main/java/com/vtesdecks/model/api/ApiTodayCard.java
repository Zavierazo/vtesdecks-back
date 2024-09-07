package com.vtesdecks.model.api;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiTodayCard {
    private LocalDate day;
    private ApiCrypt card;
}
