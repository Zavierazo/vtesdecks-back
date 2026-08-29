package com.vtesdecks.model.twda;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDate;

/**
 * The tournament of a TWDA deck. {@code playersCount} and {@code rounds} are {@code 0} when the
 * archive does not know the attendance or the number of preliminary rounds. {@code place} is a
 * free text location that usually already contains the country name (e.g. "Paris, France").
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TwdaEvent {
    private String name;
    private LocalDate date;
    private String format;
    private Integer playersCount;
    private Integer rounds;
    private String place;
    private TwdaCountry country;
    private String url;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TwdaCountry {
        private String name;
        private String code;
        private String flag;
        private String continent;
    }
}
