package com.vtesdecks.cache.indexable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CardErrata {
    private Integer cardId;
    private LocalDate effectiveDate;
    private String description;
}
