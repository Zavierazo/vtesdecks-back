package com.vtesdecks.api.controller;

import com.vtesdecks.api.GlobalExceptionHandler;
import com.vtesdecks.api.service.ApiPushSubscriptionService;
import com.vtesdecks.api.service.ApiUserNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ApiUserNotificationControllerTest {
    @Mock
    private ApiUserNotificationService userNotificationService;
    @Mock
    private ApiPushSubscriptionService pushSubscriptionService;
    @InjectMocks
    private ApiUserNotificationController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void defaultsToFirstPageOfFifty() throws Exception {
        when(userNotificationService.getUserNotifications(0, 50)).thenReturn(List.of());

        mockMvc.perform(get("/api/1.0/user/notifications"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(userNotificationService).getUserNotifications(0, 50);
    }

    @Test
    void passesRequestedPageAndLimit() throws Exception {
        when(userNotificationService.getUserNotifications(2, 25)).thenReturn(List.of());

        mockMvc.perform(get("/api/1.0/user/notifications?page=2&limit=25"))
                .andExpect(status().isOk());

        verify(userNotificationService).getUserNotifications(2, 25);
    }

    @Test
    void mapsInvalidPaginationToBadRequest() throws Exception {
        when(userNotificationService.getUserNotifications(-1, 50))
                .thenThrow(new IllegalArgumentException("Page must be zero or greater"));

        mockMvc.perform(get("/api/1.0/user/notifications?page=-1"))
                .andExpect(status().isBadRequest());
    }
}
