package com.vtesdecks.model.archon;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArchonDeck {
    private String uid;
    private String tournamentUid;
    private String name;
    private String comments;
    private String userUid;
    private Integer round;
    private Boolean winner;
    private Map<Integer, Integer> cards = new LinkedHashMap<>();
}
