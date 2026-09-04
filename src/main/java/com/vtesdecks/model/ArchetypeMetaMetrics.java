package com.vtesdecks.model;

/** Period-specific metagame counts used while mapping an archetype response. */
public record ArchetypeMetaMetrics(
        long currentCount,
        long currentTotal,
        Long previousCount,
        Long previousTotal
) {
}
