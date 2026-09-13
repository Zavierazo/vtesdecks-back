package com.vtesdecks.cache.indexable.deck;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class Stats extends SummaryStats {
    private List<DisciplineStat> cryptDisciplines = new ArrayList<>();
    private List<DisciplineStat> libraryDisciplines = new ArrayList<>();
    private List<ClanStat> libraryClans = new ArrayList<>();
}
