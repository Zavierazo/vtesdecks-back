package com.vtesdecks.model.scanner;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class ScanResponse {
    private boolean found;
    private String id;
    private String set;
    private Integer confidence;
    private Integer score;

    @JsonProperty("elapsed_ms")
    private Integer elapsedMs;

    private String message;
    private List<ScanAlternative> alternatives;
}

