package com.vtesdecks.model.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiAdminUserAccess {
    private Boolean admin;
    private List<String> roles;
}
