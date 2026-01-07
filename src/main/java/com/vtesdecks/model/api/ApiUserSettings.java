package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiUserSettings {
    private String profileImage;
    private String displayName;
    private String password;
    private String newPassword;
}
