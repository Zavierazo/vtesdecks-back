package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDate;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiErrata {
    private Integer id;
    private String name;
    private LocalDate effectiveDate;
    private String description;
    private Boolean requiresWarning;
}
