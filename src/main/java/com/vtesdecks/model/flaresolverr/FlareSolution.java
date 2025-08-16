package com.vtesdecks.model.flaresolverr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlareSolution {
    private String url;
    private String userAgent;
    private String response;
}
