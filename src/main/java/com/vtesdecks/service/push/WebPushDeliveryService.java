package com.vtesdecks.service.push;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vtesdecks.api.service.ApiPushSubscriptionService;
import com.vtesdecks.jpa.entity.UserNotificationEntity;
import com.vtesdecks.jpa.entity.UserPushSubscriptionEntity;
import com.vtesdecks.jpa.repositories.UserPushSubscriptionRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebPushDeliveryService {
    private static final int MAX_BODY_LENGTH = 500;
    private final UserPushSubscriptionRepository subscriptionRepository;
    private final ApiPushSubscriptionService subscriptionService;
    private final ObjectMapper objectMapper;

    @Value("${push.vapid.public-key:}")
    private String publicKey;

    @Value("${push.vapid.private-key:}")
    private String privateKey;

    @Value("${push.vapid.subject:mailto:support@vtesdecks.com}")
    private String subject;

    private PushService pushService;

    @PostConstruct
    void initialize() {
        if (!subscriptionService.isConfigured()) {
            return;
        }
        try {
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(new BouncyCastleProvider());
            }
            pushService = new PushService(publicKey, privateKey, subject);
        } catch (Exception e) {
            log.error("Unable to initialize Web Push delivery", e);
        }
    }

    @Async
    @Transactional
    public void deliver(UserNotificationEntity userNotification) {
        if (pushService == null || userNotification == null || userNotification.getUser() == null) {
            return;
        }
        List<UserPushSubscriptionEntity> subscriptions = subscriptionRepository.findByUser(userNotification.getUser());
        if (subscriptions.isEmpty()) {
            return;
        }
        String payload;
        try {
            payload = createPayload(userNotification);
        } catch (JsonProcessingException e) {
            log.warn("Unable to serialize Web Push payload for notification {}", userNotification.getId(), e);
            return;
        }

        int delivered = 0;
        int removed = 0;
        int failed = 0;
        for (UserPushSubscriptionEntity subscription : subscriptions) {
            try {
                Notification notification = new Notification(
                        subscription.getEndpoint(),
                        subscription.getP256dh(),
                        subscription.getAuth(),
                        payload
                );
                // The one-argument send overload still uses the legacy aesgcm encoding,
                // whose Crypto-Key header is rejected by current Chromium push services.
                HttpResponse response = pushService.send(notification, Encoding.AES128GCM);
                int status = response.getStatusLine().getStatusCode();
                if (status >= 200 && status < 300) {
                    delivered++;
                } else if (status == 404 || status == 410) {
                    subscriptionRepository.deleteById(subscription.getId());
                    removed++;
                    log.info("Removed expired Web Push subscription id {} after HTTP {} for notification {}",
                            subscription.getId(), status, userNotification.getId());
                } else {
                    failed++;
                    log.warn("Web Push provider rejected subscription id {} for notification {}: HTTP {} {}; provider response: {}",
                            subscription.getId(), userNotification.getId(), status,
                            response.getStatusLine().getReasonPhrase(), providerError(response));
                }
            } catch (Exception e) {
                failed++;
                log.warn("Web Push delivery failed for subscription id {} and notification {}: {}: {}",
                        subscription.getId(), userNotification.getId(), e.getClass().getSimpleName(), e.getMessage());
                log.debug("Web Push delivery stack trace for subscription id {} and notification {}",
                        subscription.getId(), userNotification.getId(), e);
            }
        }
        log.info("Web Push notification {} processed: delivered={}, expired={}, failed={}",
                userNotification.getId(), delivered, removed, failed);
    }

    String createPayload(UserNotificationEntity userNotification) throws JsonProcessingException {
        String body = Jsoup.parse(userNotification.getNotification()).text();
        int codePointCount = body.codePointCount(0, body.length());
        if (codePointCount > MAX_BODY_LENGTH) {
            int end = body.offsetByCodePoints(0, MAX_BODY_LENGTH - 3);
            body = body.substring(0, end) + "...";
        }

        Map<String, Object> defaultAction = Map.of(
                "operation", "openWindow",
                "url", userNotification.getLink()
        );
        Map<String, Object> data = new LinkedHashMap<>();
        if (userNotification.getId() != null) {
            data.put("notificationId", userNotification.getId());
        }
        data.put("onActionClick", Map.of("default", defaultAction));
        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("title", "VTESDecks");
        notification.put("body", body);
        notification.put("icon", "/assets/icons/icon_x192.png");
        notification.put("badge", "/assets/icons/icon_x72.png");
        notification.put("data", data);
        return objectMapper.writeValueAsString(Map.of("notification", notification));
    }

    private String providerError(HttpResponse response) {
        if (response.getEntity() == null) {
            return "<empty>";
        }
        try (InputStream content = response.getEntity().getContent()) {
            String error = new String(content.readNBytes(2048), StandardCharsets.UTF_8)
                    .replaceAll("[\\r\\n\\t]+", " ")
                    .trim();
            return error.isEmpty() ? "<empty>" : error;
        } catch (Exception e) {
            return "<unavailable: " + e.getClass().getSimpleName() + ">";
        }
    }
}
