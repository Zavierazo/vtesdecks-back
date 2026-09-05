package com.vtesdecks.model.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiAdminUser {
    private String user;
    private String displayName;
    private String profileImage;
    private String email;
    private Boolean validated;
    private Boolean admin;
    private List<String> roles;
    private List<String> availableRoles;
}
