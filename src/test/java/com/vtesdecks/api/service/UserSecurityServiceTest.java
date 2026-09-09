package com.vtesdecks.api.service;

import com.vtesdecks.configuration.JWTAuthorizationFilter;
import com.vtesdecks.jpa.entity.UserEntity;
import com.vtesdecks.jpa.repositories.UserEmailActionRepository;
import com.vtesdecks.jpa.repositories.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserSecurityServiceTest {
    private static final String KEY = "a".repeat(64);
    private static final long DAY = 24L * 60 * 60 * 1000;
    private final UserRepository users = mock(UserRepository.class);
    private final UserEmailActionRepository actions = mock(UserEmailActionRepository.class);
    private final UserSecurityService security = new UserSecurityService(users, actions);
    private UserEntity user;

    @BeforeEach
    void setUp() {
        user = new UserEntity();
        user.setId(42);
        user.setValidated(true);
        user.setAdmin(false);
        when(users.findById(42)).thenReturn(Optional.of(user));
        when(users.selectRolesByUserId(42)).thenReturn(List.of("supporter"));
    }

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void legacyLoginWorksOnlyDuringCompatibilityAndEmailTokensNeverWork() throws Exception {
        assertTrue(authorize(token(30 * DAY, null, null), false));
        assertFalse(authorize(token(30 * DAY, null, null), true));
        assertFalse(authorize(token(DAY, null, null), false));
        assertFalse(authorize(token(DAY, null, null), true));
        user.setAuthVersion(1);
        assertFalse(authorize(token(30 * DAY, null, null), false));
    }

    @Test
    void currentVersionWorksInBothModesAndRevokedVersionFails() throws Exception {
        String jwt = token(30 * DAY, "access", 0);
        assertTrue(authorize(jwt, false));
        assertTrue(authorize(jwt, true));
        security.revoke(user);
        verify(users).save(user);
        verify(actions).deleteByUserId(42);
        assertFalse(authorize(jwt, false));
        assertFalse(authorize(jwt, true));
        assertTrue(authorize(token(30 * DAY, "access", 1), true));
    }

    @Test
    void malformedNewClaimsCannotFallBackToLegacy() throws Exception {
        for (String jwt : List.of(token(30 * DAY, "access", null), token(30 * DAY, null, 0),
                token(30 * DAY, "reset", 0), token(30 * DAY, "access", "0"),
                token(30 * DAY, "access", 0.0))) {
            assertFalse(authorize(jwt, false));
        }
    }

    @Test
    void embeddedAdminRoleIsIgnoredAndUnavailableAccountsFail() throws Exception {
        assertTrue(authorize(token(30 * DAY, "access", 0), true));
        var authorities = SecurityContextHolder.getContext().getAuthentication().getAuthorities();
        assertEquals(List.of("USER", "SUPPORTER"), authorities.stream().map(Object::toString).toList());
        user.setValidated(false);
        assertFalse(authorize(token(30 * DAY, "access", 0), true));
        when(users.findById(42)).thenReturn(Optional.empty());
        assertFalse(authorize(token(30 * DAY, "access", 0), true));
    }

    @Test
    void recoveryEndpointIgnoresStaleLoginHeader() throws Exception {
        var request = new MockHttpServletRequest("PUT", "/api/1.0/auth/reset-password");
        request.addHeader("Authorization", "invalid-login-header");
        var invoked = new AtomicBoolean();
        new JWTAuthorizationFilter(KEY, security, true).doFilter(request, new MockHttpServletResponse(),
                (req, res) -> invoked.set(true));
        assertTrue(invoked.get());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private boolean authorize(String jwt, boolean rejectLegacy) throws Exception {
        SecurityContextHolder.clearContext();
        var request = new MockHttpServletRequest("GET", "/api/1.0/user/refresh");
        request.addHeader("Authorization", jwt);
        var response = new MockHttpServletResponse();
        var invoked = new AtomicBoolean();
        new JWTAuthorizationFilter(KEY, security, rejectLegacy).doFilter(request, response, (req, res) -> invoked.set(true));
        if (!invoked.get()) {
            assertEquals(403, response.getStatus());
        }
        return invoked.get();
    }

    private String token(long lifetime, String purpose, Object version) {
        long now = System.currentTimeMillis();
        return Jwts.builder().subject("42").claim("authorities", List.of("USER", "ADMIN"))
                .claim("token_use", purpose).claim("auth_version", version)
                .issuedAt(new Date(now)).expiration(new Date(now + lifetime))
                .signWith(Keys.hmacShaKeyFor(KEY.getBytes())).compact();
    }
}
