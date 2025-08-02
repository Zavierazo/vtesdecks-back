package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vtesdecks.model.CardCondition;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiCollectionCard {
    private Integer id;
    private Integer cardId;
    private String cardName;
    private String set;
    private Integer number;
    private Integer binderId;
    private CardCondition condition;
    private String language;
    private String notes;
    private LocalDateTime creationDate;
    private LocalDateTime modificationDate;
}
