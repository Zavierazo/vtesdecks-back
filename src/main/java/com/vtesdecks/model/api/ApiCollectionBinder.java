package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiCollectionBinder {
    private Integer id;
    private String name;
    private String description;
    private String icon;
    private boolean publicVisibility;
    private String publicHash;
    private LocalDateTime creationDate;
    private LocalDateTime modificationDate;
}
