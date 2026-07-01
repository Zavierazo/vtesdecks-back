package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vtesdecks.enums.DeckCardAction;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiCollectionCardHistory {
    private DeckCardAction action;
    private Integer cardId;
    private String cardName;
    private Integer number;
    private String set;
    private String condition;
    private String language;
    private Integer binderId;
    private LocalDateTime date;
}
