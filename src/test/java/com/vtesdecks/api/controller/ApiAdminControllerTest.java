package com.vtesdecks.api.controller;

import com.vtesdecks.api.service.ApiAdminSchedulerService;
import com.vtesdecks.api.service.ApiAdminUserService;
import com.vtesdecks.api.service.PasswordResetService;
import com.vtesdecks.model.api.ApiAdminUser;
import com.vtesdecks.service.FeatureFlagService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiAdminControllerTest {
    @Mock
    private ApiAdminUserService userService;
    @Mock
    private ApiAdminSchedulerService schedulerService;
    @Mock
    private FeatureFlagService featureFlagService;
    private ApiAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new ApiAdminController(userService, schedulerService, featureFlagService);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("42", null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void controllerRequiresAdminAuthority() {
        Secured secured = ApiAdminController.class.getAnnotation(Secured.class);
        assertArrayEquals(new String[]{"ADMIN"}, secured.value());
    }

    @Test
    void getUserReturnsNotFoundForUnknownUser() {
        when(userService.get("missing")).thenReturn(Optional.empty());
        assertEquals(404, controller.getUser("missing").getStatusCode().value());
    }

    @Test
    void resetMapsServiceResultsToHttpStatuses() {
        when(userService.sendPasswordReset("target", 42))
                .thenReturn(PasswordResetService.Result.SENT)
                .thenReturn(PasswordResetService.Result.COOLDOWN)
                .thenReturn(PasswordResetService.Result.USER_NOT_FOUND);

        assertEquals(204, controller.sendUserPasswordReset("target").getStatusCode().value());
        assertEquals(429, controller.sendUserPasswordReset("target").getStatusCode().value());
        assertEquals(404, controller.sendUserPasswordReset("target").getStatusCode().value());
    }

    @Test
    void validateReturnsUpdatedManagementView() {
        ApiAdminUser user = ApiAdminUser.builder().user("target").validated(true).build();
        when(userService.validate("target", 42)).thenReturn(Optional.of(user));

        assertEquals(user, controller.validateUser("target").getBody());
    }

    @Test
    void schedulerRunMapsKnownAndUnknownKeys() {
        when(schedulerService.run("known", 42)).thenReturn(true);
        when(schedulerService.run("unknown", 42)).thenReturn(false);

        assertEquals(204, controller.runScheduler("known").getStatusCode().value());
        assertEquals(404, controller.runScheduler("unknown").getStatusCode().value());
    }
}
