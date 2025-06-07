package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiUser {
    private String user;
    private String email;
    private String token;
    private String displayName;
    private String profileImage;
    private Boolean admin;
    private List<String> roles;
    private Integer notificationCount;
    private String message;
}
