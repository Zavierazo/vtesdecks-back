package com.vtesdecks.model.api;

import lombok.Data;

@Data
public class ApiPushSubscription {
    private String endpoint;
    private Long expirationTime;
    private Keys keys;

    @Data
    public static class Keys {
        private String p256dh;
        private String auth;
    }
}
