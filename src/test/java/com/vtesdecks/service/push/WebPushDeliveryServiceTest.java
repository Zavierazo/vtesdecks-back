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
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Test
    void encryptsSignsAndDeliversToLocalPushEndpoint() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        KeyPairGenerator generator = KeyPairGenerator.getInstance("ECDH", BouncyCastleProvider.PROVIDER_NAME);
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        var vapid = generator.generateKeyPair();
        AtomicReference<byte[]> received = new AtomicReference<>();
        AtomicReference<String> encoding = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/push", exchange -> {
            received.set(exchange.getRequestBody().readAllBytes());
            encoding.set(exchange.getRequestHeaders().getFirst("Content-Encoding"));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.sendResponseHeaders(201, -1);
            exchange.close();
        });
        server.start();
        try {
            String payload = "{\"notification\":{\"title\":\"Fixture\"}}";
            Notification notification = new Notification("http://127.0.0.1:" + server.getAddress().getPort() + "/push",
                    validP256dh(), Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]), payload);
            HttpResponse response = new PushService(vapid, "mailto:fixture@example.com")
                    .send(notification, Encoding.AES128GCM);
            assertEquals(201, response.getStatusLine().getStatusCode());
            assertEquals("aes128gcm", encoding.get());
            assertTrue(authorization.get().startsWith("vapid "));
            assertTrue(received.get().length > payload.length());
            assertFalse(new String(received.get(), StandardCharsets.UTF_8).contains(payload));
        } finally {
            server.stop(0);
        }
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
