package com.vtesdecks.model.twda;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDate;

/**
 * The tournament of a TWDA deck. {@code playersCount} is {@code 0} when the archive does not know
 * the attendance.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TwdaEvent {
    private String name;
    private LocalDate date;
    private String format;
    private Integer playersCount;
    private String place;
    private String url;
}
