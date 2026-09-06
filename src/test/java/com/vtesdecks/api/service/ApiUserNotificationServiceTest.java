package com.vtesdecks.api.service;

import com.vtesdecks.api.mapper.ApiUserNotificationMapper;
import com.vtesdecks.jpa.entity.UserNotificationEntity;
import com.vtesdecks.jpa.repositories.UserFollowerRepository;
import com.vtesdecks.jpa.repositories.UserNotificationRepository;
import com.vtesdecks.jpa.repositories.UserRepository;
import com.vtesdecks.model.api.ApiUserNotification;
import com.vtesdecks.service.DeckService;
import com.vtesdecks.service.push.WebPushDeliveryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiUserNotificationServiceTest {
    @Mock
    private UserNotificationRepository userNotificationRepository;
    @Mock
    private ApiUserNotificationMapper apiUserNotificationMapper;
    @Mock
    private DeckService deckService;
    @Mock
    private UserFollowerRepository userFollowerRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private WebPushDeliveryService webPushDeliveryService;
    @InjectMocks
    private ApiUserNotificationService service;

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("42", null, List.of()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void loadsRequestedNotificationPageBeforeMapping() {
        List<UserNotificationEntity> entities = List.of(new UserNotificationEntity());
        List<ApiUserNotification> mapped = List.of(new ApiUserNotification());
        when(userNotificationRepository.findByUserOrderByCreationDateDescIdDesc(
                org.mockito.ArgumentMatchers.eq(42), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(entities);
        when(apiUserNotificationMapper.map(entities)).thenReturn(mapped);

        assertEquals(mapped, service.getUserNotifications(2, 50));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(userNotificationRepository).findByUserOrderByCreationDateDescIdDesc(
                org.mockito.ArgumentMatchers.eq(42), pageable.capture());
        assertEquals(2, pageable.getValue().getPageNumber());
        assertEquals(50, pageable.getValue().getPageSize());
        assertEquals(100, pageable.getValue().getOffset());
    }

    @Test
    void rejectsInvalidPagination() {
        assertThrows(IllegalArgumentException.class, () -> service.getUserNotifications(-1, 50));
        assertThrows(IllegalArgumentException.class, () -> service.getUserNotifications(0, 0));
        assertThrows(IllegalArgumentException.class, () -> service.getUserNotifications(0, 101));
        assertThrows(IllegalArgumentException.class,
                () -> service.getUserNotifications(Integer.MAX_VALUE, 100));
    }
}
