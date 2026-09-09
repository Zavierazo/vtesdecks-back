package com.vtesdecks.model.api;

import lombok.Getter;
import lombok.Setter;

// Intentionally no generated toString: these fields contain credentials.
@Getter
@Setter
public class ApiEmailAction {
    private String token;
    private String password;
}
