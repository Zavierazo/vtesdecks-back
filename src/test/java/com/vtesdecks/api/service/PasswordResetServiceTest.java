package com.vtesdecks.api.service;

import com.vtesdecks.jpa.entity.UserEntity;
import com.vtesdecks.jpa.repositories.UserRepository;
import com.vtesdecks.service.MailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private ApiUserService userService;
    @Mock
    private MailService mailService;
    private PasswordResetService service;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(userRepository, userService, mailService);
        user = new UserEntity();
        user.setId(3);
        user.setEmail("user@example.com");
    }

    @Test
    void sendsResetAndPersistsCooldownTimestamp() {
        when(userRepository.selectRolesByUserId(3)).thenReturn(List.of("tester"));
        when(userService.getJWTToken(user, List.of("tester"), true)).thenReturn("token");

        assertEquals(PasswordResetService.Result.SENT, service.request(user));

        verify(mailService).sendForgotPasswordMail("user@example.com", "token");
        verify(userRepository).save(user);
    }

    @Test
    void enforcesThirtyMinuteCooldown() {
        user.setForgotPasswordDate(LocalDateTime.now().minusMinutes(5));

        assertEquals(PasswordResetService.Result.COOLDOWN, service.request(user));

        verify(mailService, never()).sendForgotPasswordMail(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(userRepository, never()).save(user);
    }

    @Test
    void returnsNotFoundForUnknownEmail() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(null);

        assertEquals(PasswordResetService.Result.USER_NOT_FOUND, service.request("missing@example.com"));
    }
}
