package com.vtesdecks.model.eternalvigilance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDate;

/**
 * Root document of a tournament winning deck published in the
 * <a href="https://github.com/gurchon-hall/eternal-vigilance">eternal-vigilance</a> repository.
 * One YAML file per event, named {@code <event_id>.yaml}.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EternalVigilanceDeck {
    private String name;
    private String location;
    private LocalDate dateStart;
    private String roundsFormat;
    private Integer playersCount;
    private String winner;
    private Long veknNumber;
    private String eventUrl;
    private String eventId;
    private String vpComment;
    private String forumPostUrl;
    private EternalVigilanceDeckList deck;
}
