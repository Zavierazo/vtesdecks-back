package com.vtesdecks.model.archon;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TournamentEvent {
    private String name;
    private String uid;
    private String format;
    private Boolean online;
    private LocalDateTime start;
    private String timezone;
    private String rank;
    private String finish;
    private String country;

    @JsonProperty("country_flag")
    private String countryFlag;
    private String state;
}