package com.vtesdecks.model.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiAchievementBadge {
    private String family;
    private String achievementId;
    private Integer tier;
    private Integer count;
    private String icon;
}
