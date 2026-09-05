package com.vtesdecks.api.service;

import com.vtesdecks.jpa.entity.UserEntity;
import com.vtesdecks.jpa.repositories.UserRepository;
import com.vtesdecks.model.api.ApiAdminUser;
import com.vtesdecks.model.api.ApiAdminUserAccess;
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
    private PasswordResetService passwordResetService;
    private ApiAdminUserService service;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        service = new ApiAdminUserService(userRepository, jdbcTemplate, passwordResetService);
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
    void reportsUnknownUserWithoutMutation() {
        when(userRepository.findByUsername("missing")).thenReturn(null);

        assertTrue(service.get("missing").isEmpty());
        assertTrue(service.updateAccess("missing", new ApiAdminUserAccess(true, List.of()), 1).isEmpty());
        assertTrue(service.validate("missing", 1).isEmpty());
        assertEquals(PasswordResetService.Result.USER_NOT_FOUND, service.sendPasswordReset("missing", 1));
    }
}
