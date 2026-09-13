package com.vtesdecks.cache.indexable.deck;

import java.util.HashSet;
import java.util.Set;

import lombok.Data;

@Data
public class ClanStat {
    private Set<String> clans = new HashSet<>();
    private Integer number;
}
