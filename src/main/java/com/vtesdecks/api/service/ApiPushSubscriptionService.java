package com.vtesdecks.api.service;

import com.vtesdecks.jpa.entity.UserPushSubscriptionEntity;
import com.vtesdecks.jpa.repositories.UserPushSubscriptionRepository;
import com.vtesdecks.model.api.ApiPushConfig;
import com.vtesdecks.model.api.ApiPushSubscription;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ApiPushSubscriptionService {
    private static final int MAX_ENDPOINT_LENGTH = 2048;
    private final UserPushSubscriptionRepository repository;

    @Value("${push.enabled:false}")
    private boolean pushEnabled;

    @Value("${push.vapid.public-key:}")
    private String publicKey;

    @Value("${push.vapid.private-key:}")
    private String privateKey;

    public ApiPushConfig config() {
        boolean enabled = isConfigured();
        return new ApiPushConfig(enabled, enabled ? publicKey : null);
    }

    public boolean isConfigured() {
        return pushEnabled && hasDecodedLength(publicKey, 65) && hasDecodedLength(privateKey, 32);
    }

    @Transactional
    public boolean register(Integer userId, ApiPushSubscription request) {
        validate(request);
        String hash = hash(request.getEndpoint());
        UserPushSubscriptionEntity subscription = repository.findByEndpointHash(hash).orElse(null);
        if (subscription != null && !userId.equals(subscription.getUser())) {
            return false;
        }
        if (subscription == null) {
            subscription = new UserPushSubscriptionEntity();
            subscription.setUser(userId);
            subscription.setEndpointHash(hash);
        }
        subscription.setEndpoint(request.getEndpoint());
        subscription.setP256dh(request.getKeys().getP256dh());
        subscription.setAuth(request.getKeys().getAuth());
        subscription.setExpirationTime(request.getExpirationTime() == null ? null : Instant.ofEpochMilli(request.getExpirationTime()));
        repository.save(subscription);
        return true;
    }

    @Transactional
    public void unregister(Integer userId, String endpoint) {
        validateEndpoint(endpoint);
        repository.deleteByUserAndEndpointHash(userId, hash(endpoint));
    }

    private void validate(ApiPushSubscription request) {
        if (!isConfigured()) {
            throw new IllegalStateException("Push notifications are not configured");
        }
        if (request == null || request.getKeys() == null) {
            throw new IllegalArgumentException("Invalid push subscription");
        }
        validateEndpoint(request.getEndpoint());
        validateBase64Url(request.getKeys().getP256dh(), "p256dh");
        validateBase64Url(request.getKeys().getAuth(), "auth");
    }

    private void validateEndpoint(String endpoint) {
        if (StringUtils.isBlank(endpoint) || endpoint.length() > MAX_ENDPOINT_LENGTH) {
            throw new IllegalArgumentException("Invalid push endpoint");
        }
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid push endpoint");
        }
        boolean standardHttpsPort = uri.getPort() == -1 || uri.getPort() == 443;
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || !standardHttpsPort || !isAllowedHost(uri.getHost())) {
            throw new IllegalArgumentException("Unsupported push endpoint");
        }
    }

    private boolean isAllowedHost(String rawHost) {
        String host = rawHost.toLowerCase(Locale.ROOT);
        return host.equals("fcm.googleapis.com")
                || host.equals("android.googleapis.com")
                || host.equals("updates.push.services.mozilla.com")
                || host.equals("push.apple.com")
                || host.endsWith(".push.apple.com")
                || host.equals("notify.windows.com")
                || host.endsWith(".notify.windows.com");
    }

    private void validateBase64Url(String value, String field) {
        if (StringUtils.isBlank(value) || value.length() > 255) {
            throw new IllegalArgumentException("Invalid " + field + " key");
        }
        byte[] decoded;
        try {
            decoded = Base64.getUrlDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid " + field + " key");
        }
        int expectedLength = "auth".equals(field) ? 16 : 65;
        if (decoded.length != expectedLength || ("p256dh".equals(field) && decoded[0] != 4)) {
            throw new IllegalArgumentException("Invalid " + field + " key");
        }
    }

    private boolean hasDecodedLength(String value, int expectedLength) {
        if (StringUtils.isBlank(value)) {
            return false;
        }
        try {
            return Base64.getUrlDecoder().decode(value).length == expectedLength;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static String hash(String endpoint) {
        return DigestUtils.sha256Hex(endpoint);
    }
}
