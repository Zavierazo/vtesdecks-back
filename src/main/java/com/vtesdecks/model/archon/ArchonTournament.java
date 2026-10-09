package com.vtesdecks.model.archon;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArchonTournament {
    private String uid;
    private String name;
    private String state;
    private LocalDateTime start;
    private String country;
    private String city;
    private String venue;
    private Integer maxRounds;
    private List<List<RoundTable>> rounds = new ArrayList<>();
    private String winner;
    private String decklistsMode;
    private String eventCode;
    private ExternalIds externalIds;
    private List<Player> players = new ArrayList<>();
    private Finals finals;

    /** Only the outer round count is needed; do not retain every table's seating/results. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RoundTable {
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExternalIds {
        private String vekn;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Player {
        private Boolean nonCompeting;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Finals {
        private String state;
        private List<Seat> seating = new ArrayList<>();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Seat {
        private String playerUid;
        private Score result;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Score {
        private Integer gw;
        private Integer tp;
        private BigDecimal vp;
    }
}
