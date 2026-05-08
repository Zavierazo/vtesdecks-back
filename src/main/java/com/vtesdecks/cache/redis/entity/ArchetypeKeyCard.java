package com.vtesdecks.cache.redis.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArchetypeKeyCard {
    private Integer id;
    private Double appearanceRate;
    private Double avg;
    private Integer min;
    private Integer max;
    private Integer number;
}

