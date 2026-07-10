package com.vtesdecks.api.service;

import com.vtesdecks.configuration.ApiSecurityConfiguration;
import com.vtesdecks.enums.CardPrintingPreference;
import com.vtesdecks.jpa.entity.UserEntity;
import com.vtesdecks.jpa.repositories.UserFollowerRepository;
import com.vtesdecks.jpa.repositories.UserRepository;
import com.vtesdecks.model.api.ApiUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ApiUserServiceTest {
    private static final String JWT_SECRET = "0123456789012345678901234567890123456789012345678901234567890123";

    @Mock
    private UserRepository userRepository;
    @Mock
    private ApiUserNotificationService userNotificationService;
    @Mock
    private ApiSecurityConfiguration securityConfiguration;
    @Mock
    private UserFollowerRepository userFollowerRepository;
    @InjectMocks
    private ApiUserService service;

    @BeforeEach
    public void setUp() {
        when(securityConfiguration.getJwtSecret()).thenReturn(JWT_SECRET);
        lenient().when(userNotificationService.notificationUnreadCount(any())).thenReturn(0);
    }

    @Test
    public void shouldIncludeCardPrintingPreferenceInUserPayload() {
        UserEntity dbUser = user();
        dbUser.setCardPrintingPreference(CardPrintingPreference.FIRST);

        ApiUser apiUser = service.getAuthenticatedUser(dbUser, List.of());

        assertNotNull(apiUser.getToken());
        assertEquals(CardPrintingPreference.FIRST, apiUser.getCardPrintingPreference());
    }

    @Test
    public void shouldDefaultCardPrintingPreferenceToNewest() {
        UserEntity dbUser = user();
        dbUser.setCardPrintingPreference(null);

        ApiUser apiUser = service.getAuthenticatedUser(dbUser, List.of());

        assertEquals(CardPrintingPreference.NEWEST, apiUser.getCardPrintingPreference());
    }

    private UserEntity user() {
        UserEntity user = new UserEntity();
        user.setId(1);
        user.setUsername("testuser");
        user.setEmail("testuser@example.com");
        user.setDisplayName("Test User");
        user.setAdmin(false);
        return user;
    }
}
