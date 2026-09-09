package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiUserSettingsResponse {
    private Boolean successful;
    private String message;
    private ApiUser authenticatedUser;
}
