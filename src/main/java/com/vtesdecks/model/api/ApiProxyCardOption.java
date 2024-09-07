package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiProxyCardOption {
    private Integer cardId;
    private String setAbbrev;
    private String setName;
    private LocalDate setReleaseDate;
    private String imageUrl;
}
