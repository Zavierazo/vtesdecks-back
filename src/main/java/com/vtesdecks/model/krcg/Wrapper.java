package com.vtesdecks.model.krcg;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Wrapper {
    private Integer count;
    private List<Card> cards;
}
