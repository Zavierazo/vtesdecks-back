package com.vtesdecks.model.flaresolverr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlareResponse {
    private String status;
    private String message;
    private String session;
    private FlareSolution solution;
}
