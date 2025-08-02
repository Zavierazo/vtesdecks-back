package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiCollection {
    private Integer id;
    private List<ApiCollectionBinder> binders;
    private boolean publicVisibility;
    private String publicHash;
    private LocalDateTime creationDate;
    private LocalDateTime modificationDate;
}