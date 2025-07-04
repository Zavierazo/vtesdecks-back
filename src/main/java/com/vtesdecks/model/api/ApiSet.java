package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiSet {
    private Integer id;
    private String abbrev;
    private LocalDate releaseDate;
    private String fullName;
    private String company;
    private String icon;
    private LocalDateTime lastUpdate;
}