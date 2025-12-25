package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CollectionSectionStats {
    private long total = 0;
    private Set<Integer> collected = new HashSet<>();
    private Set<Integer> missing = new HashSet<>();
    private BigDecimal price = BigDecimal.ZERO;
}
