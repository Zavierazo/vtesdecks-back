package com.vtesdecks.model.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiAchievementTier {
    private String id;
    private Integer threshold;
    private Boolean earned;
    private ZonedDateTime earnedDate;
    private Boolean historical;
}
