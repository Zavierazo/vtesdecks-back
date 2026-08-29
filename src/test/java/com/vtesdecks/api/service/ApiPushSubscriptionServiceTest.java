package com.vtesdecks.api.service;

import com.vtesdecks.jpa.entity.UserPushSubscriptionEntity;
import com.vtesdecks.jpa.repositories.UserPushSubscriptionRepository;
import com.vtesdecks.model.api.ApiPushSubscription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiPushSubscriptionServiceTest {
    private static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/example";

    @Mock
    private UserPushSubscriptionRepository repository;

    @InjectMocks
    private ApiPushSubscriptionService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "pushEnabled", true);
        ReflectionTestUtils.setField(service, "publicKey", base64Bytes(65, (byte) 4));
        ReflectionTestUtils.setField(service, "privateKey", base64Bytes(32, (byte) 1));
    }

    @Test
    void registersAValidSubscription() {
        when(repository.findByEndpointHash(ApiPushSubscriptionService.hash(ENDPOINT))).thenReturn(Optional.empty());

        assertTrue(service.register(7, subscription(ENDPOINT)));

        ArgumentCaptor<UserPushSubscriptionEntity> captor = ArgumentCaptor.forClass(UserPushSubscriptionEntity.class);
        verify(repository).save(captor.capture());
        assertEquals(7, captor.getValue().getUser());
        assertEquals(ENDPOINT, captor.getValue().getEndpoint());
        assertEquals(ApiPushSubscriptionService.hash(ENDPOINT), captor.getValue().getEndpointHash());
    }

    @Test
    void updatesAnExistingSubscriptionForTheSameUser() {
        UserPushSubscriptionEntity existing = new UserPushSubscriptionEntity();
        existing.setId(10);
        existing.setUser(7);
        when(repository.findByEndpointHash(ApiPushSubscriptionService.hash(ENDPOINT))).thenReturn(Optional.of(existing));

        assertTrue(service.register(7, subscription(ENDPOINT)));

        verify(repository).save(existing);
        assertEquals(base64Bytes(65, (byte) 4), existing.getP256dh());
        assertEquals(base64Bytes(16, (byte) 1), existing.getAuth());
    }

    @Test
    void refusesAnEndpointOwnedByAnotherUser() {
        UserPushSubscriptionEntity existing = new UserPushSubscriptionEntity();
        existing.setUser(8);
        when(repository.findByEndpointHash(ApiPushSubscriptionService.hash(ENDPOINT))).thenReturn(Optional.of(existing));

        assertFalse(service.register(7, subscription(ENDPOINT)));

        verify(repository, never()).save(existing);
    }

    @Test
    void rejectsUnsupportedOrInsecureEndpoints() {
        assertThrows(IllegalArgumentException.class,
                () -> service.register(7, subscription("http://fcm.googleapis.com/example")));
        assertThrows(IllegalArgumentException.class,
                () -> service.register(7, subscription("https://example.com/push")));
    }

    @Test
    void unregistersOnlyTheCurrentUsersEndpoint() {
        service.unregister(7, ENDPOINT);

        verify(repository).deleteByUserAndEndpointHash(7, ApiPushSubscriptionService.hash(ENDPOINT));
    }

    private ApiPushSubscription subscription(String endpoint) {
        ApiPushSubscription.Keys keys = new ApiPushSubscription.Keys();
        keys.setP256dh(base64Bytes(65, (byte) 4));
        keys.setAuth(base64Bytes(16, (byte) 1));
        ApiPushSubscription subscription = new ApiPushSubscription();
        subscription.setEndpoint(endpoint);
        subscription.setExpirationTime(null);
        subscription.setKeys(keys);
        return subscription;
    }

    private String base64Bytes(int length, byte firstByte) {
        byte[] value = new byte[length];
        value[0] = firstByte;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
