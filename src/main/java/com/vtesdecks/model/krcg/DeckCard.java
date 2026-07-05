package com.vtesdecks.model.krcg;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeckCard {
    private Integer id;
    @JsonProperty("printed_name")
    private String name;
    private Integer count;
}
