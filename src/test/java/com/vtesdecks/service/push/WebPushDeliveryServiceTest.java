package com.vtesdecks.service.push;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vtesdecks.api.service.ApiPushSubscriptionService;
import com.vtesdecks.jpa.entity.UserNotificationEntity;
import com.vtesdecks.jpa.entity.UserPushSubscriptionEntity;
import com.vtesdecks.jpa.repositories.UserPushSubscriptionRepository;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Utils;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebPushDeliveryServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebPushDeliveryService service = new WebPushDeliveryService(
            mock(UserPushSubscriptionRepository.class),
            mock(ApiPushSubscriptionService.class),
            objectMapper
    );

    @Test
    void createsAnAngularServiceWorkerPayloadWithPlainTextAndLink() throws Exception {
        UserNotificationEntity notification = new UserNotificationEntity();
        notification.setId(42);
        notification.setNotification("<strong>New deck</strong><br/>Deck &amp; name");
        notification.setLink("/deck/example");

        JsonNode payload = objectMapper.readTree(service.createPayload(notification));
        JsonNode browserNotification = payload.get("notification");

        assertEquals("VTESDecks", browserNotification.get("title").asText());
        assertEquals("New deck Deck & name", browserNotification.get("body").asText());
        assertFalse(browserNotification.get("body").asText().contains("<"));
        assertEquals("openWindow", browserNotification.at("/data/onActionClick/default/operation").asText());
        assertEquals("/deck/example", browserNotification.at("/data/onActionClick/default/url").asText());
    }

    @Test
    void truncatesLongNotificationBodies() throws Exception {
        UserNotificationEntity notification = new UserNotificationEntity();
        notification.setNotification("x".repeat(600));
        notification.setLink("/");

        JsonNode payload = objectMapper.readTree(service.createPayload(notification));

        assertEquals(500, payload.at("/notification/body").asText().length());
    }

    @Test
    void sendsUsingTheModernAes128GcmEncoding() throws Exception {
        UserPushSubscriptionRepository repository = mock(UserPushSubscriptionRepository.class);
        PushService pushService = mock(PushService.class);
        HttpResponse response = mock(HttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(201);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(pushService.send(any(Notification.class), any(Encoding.class))).thenReturn(response);

        UserPushSubscriptionEntity subscription = new UserPushSubscriptionEntity();
        subscription.setId(4);
        subscription.setUser(7);
        subscription.setEndpoint("https://fcm.googleapis.com/example");
        subscription.setP256dh(validP256dh());
        subscription.setAuth(Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]));
        when(repository.findByUser(7)).thenReturn(List.of(subscription));

        WebPushDeliveryService deliveryService = new WebPushDeliveryService(
                repository,
                mock(ApiPushSubscriptionService.class),
                objectMapper
        );
        ReflectionTestUtils.setField(deliveryService, "pushService", pushService);
        UserNotificationEntity notification = new UserNotificationEntity();
        notification.setId(42);
        notification.setUser(7);
        notification.setNotification("Test");
        notification.setLink("/");

        deliveryService.deliver(notification);

        verify(pushService).send(any(Notification.class), org.mockito.ArgumentMatchers.eq(Encoding.AES128GCM));
    }

    private String validP256dh() throws Exception {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        KeyPairGenerator generator = KeyPairGenerator.getInstance("ECDH", BouncyCastleProvider.PROVIDER_NAME);
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        ECPublicKey key = (ECPublicKey) generator.generateKeyPair().getPublic();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(Utils.encode(key));
    }
}
