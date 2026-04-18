package com.vtesdecks.model.scanner;

import lombok.Data;

@Data
public class ScanAlternative {
    private String id;
    private String set;
    private Integer score;
    private Integer confidence;
}

