package com.vtesdecks.model.archon;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArchonAccessToken {
    @JsonProperty("access_token")
    private String accessToken;
}
