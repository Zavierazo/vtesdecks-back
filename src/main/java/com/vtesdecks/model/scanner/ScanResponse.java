package com.vtesdecks.model.scanner;

import lombok.Data;

import java.util.List;

@Data
public class ScanResponse {
    private boolean found;
    private String id;
    private String set;
    private Integer confidence;
    private Integer score;
    private Integer elapsedMs;
    private String message;
    private List<ScanAlternative> alternatives;
}

