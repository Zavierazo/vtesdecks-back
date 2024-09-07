package com.vtesdecks.cache.indexable.deck;

import java.util.Set;

import lombok.Data;

@Data
public class DisciplineStat {
    private Set<String> disciplines;
    private Integer inferior;
    private Integer superior;
}
