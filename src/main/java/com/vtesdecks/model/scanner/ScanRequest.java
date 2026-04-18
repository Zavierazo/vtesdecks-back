package com.vtesdecks.model.scanner;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonProperty("id_only")
    @Builder.Default
    private boolean idOnly = false;

    @JsonProperty("no_alternatives")
    @Builder.Default
    private boolean noAlternatives = false;

    @Builder.Default
    private boolean fast = false;
}

