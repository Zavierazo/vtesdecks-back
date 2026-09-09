package com.vtesdecks.api.service;

import com.vtesdecks.jpa.entity.UserEntity;
import com.vtesdecks.jpa.repositories.UserRepository;
import com.vtesdecks.model.api.ApiAdminUser;
import com.vtesdecks.model.api.ApiAdminUserAccess;
import com.vtesdecks.model.api.ApiUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiAdminUserServiceTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private UserSecurityService security;
    @Mock
    private PasswordResetService passwordResetService;
    @Mock
    private ApiUserService apiUserService;
    private ApiAdminUserService service;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        service = new ApiAdminUserService(userRepository, jdbcTemplate, passwordResetService, apiUserService, security);
        user = new UserEntity();
        user.setId(7);
        user.setUsername("target");
        user.setDisplayName("Target User");
        user.setEmail("target@example.com");
        user.setValidated(false);
        user.setAdmin(false);
    }

    @Test
    void returnsPrivateManagementDataAndDistinctSortedRoles() {
        when(userRepository.findByUsername("target")).thenReturn(user);
        when(userRepository.selectRolesByUserId(7)).thenReturn(List.of("tester", "supporter", "tester"));
        when(jdbcTemplate.queryForList(anyString(), eq(String.class))).thenReturn(List.of("supporter", "tester"));

        ApiAdminUser result = service.get("target").orElseThrow();

        assertEquals("target@example.com", result.getEmail());
        assertEquals(List.of("supporter", "tester"), result.getRoles());
        assertFalse(result.getValidated());
    }

    @Test
    void findsUserByEmailWhenUsernameDoesNotMatch() {
        when(userRepository.findByUsername("target@example.com")).thenReturn(null);
        when(userRepository.findByEmailIgnoreCase("target@example.com")).thenReturn(user);
        when(userRepository.selectRolesByUserId(7)).thenReturn(List.of());
        when(jdbcTemplate.queryForList(anyString(), eq(String.class))).thenReturn(List.of());

        ApiAdminUser result = service.get("target@example.com").orElseThrow();

        assertEquals("target", result.getUser());
    }

    @Test
    void replacesCompleteRoleSetAndAllowsAdminSelfDemotion() {
        user.setAdmin(true);
        when(userRepository.findByUsername("target")).thenReturn(user);
        when(jdbcTemplate.queryForList(anyString(), eq(String.class))).thenReturn(List.of("supporter", "tester"));
        lenient().doReturn(1).when(jdbcTemplate).update(anyString(), eq(7), eq("supporter"));
        lenient().doReturn(1).when(jdbcTemplate).update(anyString(), eq(7), eq("tester"));

        ApiAdminUser result = service.updateAccess(
                "target", new ApiAdminUserAccess(false, List.of("tester", "supporter", "tester")), 7)
                .orElseThrow();

        assertFalse(result.getAdmin());
        assertEquals(List.of("supporter", "tester"), result.getRoles());
        verify(userRepository).save(user);
        verify(jdbcTemplate).update(anyString(), eq(7));
        verify(jdbcTemplate, times(1)).update(anyString(), eq(7), eq("supporter"));
        verify(jdbcTemplate, times(1)).update(anyString(), eq(7), eq("tester"));
        verify(security, never()).revoke(user);
    }

    @Test
    void rejectsUnknownRoleBeforeChangingTheUser() {
        when(userRepository.findByUsername("target")).thenReturn(user);
        when(jdbcTemplate.queryForList(anyString(), eq(String.class))).thenReturn(List.of("tester"));

        assertThrows(IllegalArgumentException.class,
                () -> service.updateAccess("target", new ApiAdminUserAccess(true, List.of("unknown")), 1));

        verify(userRepository, never()).save(user);
        verify(jdbcTemplate, never()).update(anyString(), eq(7));
    }

    @Test
    void validatesAccountIdempotently() {
        when(userRepository.findByUsername("target")).thenReturn(user);
        when(userRepository.selectRolesByUserId(7)).thenReturn(List.of());
        when(jdbcTemplate.queryForList(anyString(), eq(String.class))).thenReturn(List.of());

        assertTrue(service.validate("target", 1).orElseThrow().getValidated());
        service.validate("target", 1);

        verify(userRepository, times(1)).save(user);
    }

    @Test
    void adminEmailUpdateIsImmediatelyTrusted() {
        when(userRepository.findByUsername("target")).thenReturn(user);
        when(userRepository.findByEmailIgnoreCase("new@example.com")).thenReturn(null);
        when(userRepository.selectRolesByUserId(7)).thenReturn(List.of());
        when(jdbcTemplate.queryForList(anyString(), eq(String.class))).thenReturn(List.of());

        ApiAdminUser result = service.updateEmail("target", " new@example.com ", 42).orElseThrow();

        assertEquals("new@example.com", result.getEmail());
        assertTrue(result.getValidated());
        verify(userRepository).save(user);
    }

    @Test
    void rejectsEmailOwnedByAnotherUser() {
        UserEntity otherUser = new UserEntity();
        otherUser.setId(8);
        when(userRepository.findByUsername("target")).thenReturn(user);
        when(userRepository.findByEmailIgnoreCase("taken@example.com")).thenReturn(otherUser);

        assertThrows(IllegalStateException.class,
                () -> service.updateEmail("target", "taken@example.com", 42));

        verify(userRepository, never()).save(user);
    }

    @Test
    void impersonationReturnsNormalAuthenticatedUserPayload() {
        ApiUser authenticatedUser = new ApiUser();
        authenticatedUser.setUser("target");
        authenticatedUser.setToken("target-token");
        when(userRepository.findByUsername("target")).thenReturn(user);
        when(userRepository.selectRolesByUserId(7)).thenReturn(List.of("supporter"));
        when(apiUserService.getAuthenticatedUser(user, List.of("supporter"))).thenReturn(authenticatedUser);

        ApiUser result = service.impersonate("target", 42).orElseThrow();

        assertEquals("target-token", result.getToken());
        verify(apiUserService).getAuthenticatedUser(user, List.of("supporter"));
    }

    @Test
    void reportsUnknownUserWithoutMutation() {
        when(userRepository.findByUsername("missing")).thenReturn(null);

        assertTrue(service.get("missing").isEmpty());
        assertTrue(service.updateAccess("missing", new ApiAdminUserAccess(true, List.of()), 1).isEmpty());
        assertTrue(service.validate("missing", 1).isEmpty());
        assertTrue(service.updateEmail("missing", "new@example.com", 1).isEmpty());
        assertTrue(service.impersonate("missing", 1).isEmpty());
        assertEquals(PasswordResetService.Result.USER_NOT_FOUND, service.sendPasswordReset("missing", 1));
    }
}
