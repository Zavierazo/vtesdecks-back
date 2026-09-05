package com.vtesdecks.model.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiAchievementTier {
    private String id;
    private Integer threshold;
    private Boolean earned;
    private LocalDateTime earnedDate;
    private Boolean historical;
}
