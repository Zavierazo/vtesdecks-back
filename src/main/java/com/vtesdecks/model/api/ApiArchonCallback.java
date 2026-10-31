package com.vtesdecks.model.api;

import lombok.Getter;
import lombok.Setter;

// Deliberately has no generated toString: authorization codes must not be logged.
@Getter
@Setter
public class ApiArchonCallback {
    private String state;
    private String code;
    private String error;
}
