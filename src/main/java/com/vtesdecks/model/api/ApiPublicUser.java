package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiPublicUser {
    private String user;
    private String displayName;
    private String profileImage;
    private List<String> roles;
    private List<ApiPublicUser> followers;
    private List<ApiPublicUser> following;
}
