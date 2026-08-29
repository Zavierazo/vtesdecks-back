package com.vtesdecks.model.api;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ApiPushConfig {
    private boolean enabled;
    private String publicKey;
}
