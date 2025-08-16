package com.vtesdecks.model.flaresolverr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlareRequest {
    private String cmd;
    private String session;
    private String url;
    private Integer maxTimeout;
}
