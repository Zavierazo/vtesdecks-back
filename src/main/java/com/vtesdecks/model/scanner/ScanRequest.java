package com.vtesdecks.model.scanner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanRequest {
    private String image;
    @Builder.Default
    private boolean idOnly = false;
    @Builder.Default
    private boolean noAlternatives = false;
}

