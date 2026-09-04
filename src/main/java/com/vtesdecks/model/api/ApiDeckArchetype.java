package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vtesdecks.model.ArchetypeTrend;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiDeckArchetype {
    private Integer id;
    private String name;
    private String icon;
    private String type;
    private String description;
    private String deckId;
    private String secondaryDeckId;
    private Boolean enabled;
    private Long deckCount;
    private Long metaCount;
    private Long metaTotal;
    private Long previousMetaCount;
    private Long previousMetaTotal;
    /** Difference between current and preceding-period metagame share, in percentage points. */
    private Double metaShareChange;
    private BigDecimal price;
    private String currency;
    private LocalDateTime creationDate;
    private LocalDateTime modificationDate;
    private List<ApiArchetypeCard> keyCrypt;
    private List<ApiArchetypeCard> keyLibrary;
    private Set<String> clans;
    private Set<String> disciplines;
    /** Trend of this archetype in the tournament meta, based on 90-day vs 91-365 day activity rate. */
    private ArchetypeTrend trend;
}

