package com.vtesdecks.configuration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.vtesdecks.api.controller.ApiAuthController;
import com.vtesdecks.api.service.ApiUserService;
import com.vtesdecks.api.service.UserSecurityService;
import com.vtesdecks.jpa.entity.UserEntity;
import com.vtesdecks.jpa.repositories.UserRepository;
import com.vtesdecks.model.api.ApiUser;
import com.vtesdecks.service.OauthService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthenticationLoggingTest {
    private final Logger logger = (Logger) LoggerFactory.getLogger("com.vtesdecks");
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private Level previousLevel;

    @BeforeEach
    void captureLogs() {
        previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void cleanup() {
        logger.detachAppender(logs);
        logger.setLevel(previousLevel);
        logs.stop();
        SecurityContextHolder.clearContext();
    }

    @Test
    void passwordAndGoogleLoginDoNotLogCredentials() {
        ApiAuthController controller = new ApiAuthController();
        UserRepository repository = mock(UserRepository.class);
        ApiUserService users = mock(ApiUserService.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        OauthService oauth = mock(OauthService.class);
        ReflectionTestUtils.setField(controller, "userRepository", repository);
        ReflectionTestUtils.setField(controller, "userService", users);
        ReflectionTestUtils.setField(controller, "passwordEncoder", encoder);
        ReflectionTestUtils.setField(controller, "oauthService", oauth);
        UserEntity dbUser = new UserEntity();
        dbUser.setId(42);
        dbUser.setPassword("stored-hash");
        dbUser.setValidated(true);
        ApiUser authenticated = new ApiUser();
        authenticated.setToken("issued-jwt-secret-marker");
        when(repository.findByUsername("testuser")).thenReturn(dbUser);
        when(repository.selectRolesByUserId(42)).thenReturn(List.of());
        when(encoder.matches("password-secret-marker", "stored-hash")).thenReturn(true);
        when(users.getAuthenticatedUser(dbUser, List.of())).thenReturn(authenticated);
        assertSame(authenticated, controller.login(new MockHttpServletRequest(),
                Map.of("username", "testuser", "password", "password-secret-marker")));
        GoogleIdToken identity = mock(GoogleIdToken.class);
        when(identity.getPayload()).thenReturn(new GoogleIdToken.Payload().setEmail("test@example.com"));
        when(oauth.validateOauthToken("google-secret-marker")).thenReturn(identity);
        when(repository.findByEmail("test@example.com")).thenReturn(dbUser);
        assertSame(authenticated, controller.oauthLogin(new MockHttpServletRequest(),
                Map.of("token", "google-secret-marker")));
        controller.oauthLogin(new MockHttpServletRequest(), Map.of("token", "invalid-secret-marker"));
        assertSanitized("issued-jwt-secret-marker", "password-secret-marker", "google-secret-marker", "invalid-secret-marker");
        assertTrue(logs.list.stream().anyMatch(e -> e.getFormattedMessage().contains("userId=42")));
    }

    @Test
    void malformedGoogleTokenDoesNotLeakThroughException() {
        assertNull(new OauthService().validateOauthToken("malformed-secret-marker"));
        assertSanitized("malformed-secret-marker");
    }

    @Test
    void rejectsMalformedExpiredAndRotatedTokensWithoutLoggingThem() throws Exception {
        String currentKey = "a".repeat(64);
        String oldKey = "b".repeat(64);
        UserSecurityService security = mock(UserSecurityService.class);
        when(security.authenticate(any(), eq(false))).thenReturn(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("42", null, List.of()));
        for (String token : List.of("malformed-secret-marker", "", jwt(currentKey, -60000), jwt(oldKey, 60000))) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/1.0/user/refresh");
            request.addHeader("Authorization", token);
            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicBoolean invoked = new AtomicBoolean();
            new JWTAuthorizationFilter(currentKey, security, false).doFilter(request, response, (req, res) -> invoked.set(true));
            assertEquals(403, response.getStatus());
            assertFalse(invoked.get());
            assertNull(SecurityContextHolder.getContext().getAuthentication());
            if (!token.isEmpty()) {
                assertSanitized(token);
            }
        }
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", jwt(currentKey, 60000));
        AtomicBoolean invoked = new AtomicBoolean();
        new JWTAuthorizationFilter(currentKey, security, false).doFilter(request, new MockHttpServletResponse(),
                (req, res) -> invoked.set(true));
        assertTrue(invoked.get());
        assertEquals("42", SecurityContextHolder.getContext().getAuthentication().getName());
    }

    private String jwt(String key, long expiresIn) {
        return Jwts.builder().subject("42").claim("authorities", List.of("USER"))
                .expiration(new Date(System.currentTimeMillis() + expiresIn))
                .signWith(Keys.hmacShaKeyFor(key.getBytes(java.nio.charset.StandardCharsets.UTF_8))).compact();
    }

    private void assertSanitized(String... secrets) {
        assertFalse(logs.list.isEmpty());
        for (ILoggingEvent event : logs.list) {
            assertNull(event.getThrowableProxy());
            for (String secret : secrets) {
                assertFalse(event.getFormattedMessage().contains(secret));
                if (event.getArgumentArray() != null) {
                    for (Object argument : event.getArgumentArray()) {
                        assertFalse(String.valueOf(argument).contains(secret));
                    }
                }
            }
        }
    }
}
