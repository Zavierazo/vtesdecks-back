package com.vtesdecks.model.tcgmarket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketResponse {
    private Long count;
    private String next;
    private String previous;
    private List<MarketResult> results;
}
