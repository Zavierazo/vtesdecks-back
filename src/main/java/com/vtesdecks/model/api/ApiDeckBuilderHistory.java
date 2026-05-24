package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vtesdecks.enums.DeckCardAction;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiDeckBuilderHistory {
    private DeckCardAction action;
    private Integer cardId;
    private Integer number;
    private LocalDateTime date;
    private Integer tag;
    private String tagLabel;
}
