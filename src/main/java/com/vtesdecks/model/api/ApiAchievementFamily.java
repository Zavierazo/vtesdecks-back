package com.vtesdecks.model.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiAchievementFamily {
    private String id;
    private String icon;
    private Integer progress;
    private Integer nextThreshold;
    private Integer repeatCount;
    private List<ApiAchievementTier> tiers;
    private List<ZonedDateTime> earnedDates;
}
