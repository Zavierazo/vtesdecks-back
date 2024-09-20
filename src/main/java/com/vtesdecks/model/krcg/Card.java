package com.vtesdecks.model.krcg;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Card {
    private Integer id;
    private String name;
    @JsonProperty("card_text")
    private String text;
    private String url;
    private Integer count;
    private String type;
    private List<Card> cards;
    @JsonProperty("_i18n")
    private Map<String, Card> i18n;
}
