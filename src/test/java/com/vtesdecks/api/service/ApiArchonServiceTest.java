package com.vtesdecks.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vtesdecks.cache.ArchonLoginCache;
import com.vtesdecks.integration.ArchonMemberClient;
import com.vtesdecks.jpa.repositories.UserRepository;
import com.vtesdecks.model.api.ApiArchonCallback;
import com.vtesdecks.service.DeckUserService;
import org.apache.commons.codec.digest.DigestUtils;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiArchonServiceTest {
    private LocalContainerEntityManagerFactoryBean factory;
    private JdbcTemplate jdbc;
    private UserRepository users;
    private ArchonLoginCache logins;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private final Map<String, String> redisValues = new ConcurrentHashMap<>();
    private JpaTransactionManager transactions;
    private ApiArchonService service;
    private ArchonMemberClient client;
    private DeckUserService deckUserService;
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ZoneOffset.UTC);

    @BeforeAll
    void database() throws Exception {
        var dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE `user` (id INT PRIMARY KEY, auth_version BIGINT NOT NULL)");
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V1.0.105__archon_account_linking.sql"))
                .replace(" CHARACTER SET ascii COLLATE ascii_bin", "")
                .replace(" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci", "");
        for (String statement : migration.split(";")) {
            if (!statement.isBlank()) { jdbc.execute(statement); }
        }
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setPackagesToScan("com.vtesdecks.jpa.entity");
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none"));
        factory.afterPropertiesSet();
        var entityManager = SharedEntityManagerCreator.createSharedEntityManager(factory.getObject());
        var repositories = new JpaRepositoryFactory(entityManager);
        users = repositories.getRepository(UserRepository.class);
        transactions = new JpaTransactionManager(factory.getObject());
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        jdbc.update("DELETE FROM `user`");
        jdbc.update("INSERT INTO `user` (id, auth_version) VALUES (7, 2), (8, 0)");
        authenticate(7);
        client = mock(ArchonMemberClient.class);
        when(client.available()).thenReturn(true);
        when(client.authorizationUrl(anyString(), anyString())).thenAnswer(call ->
                "https://archon.vekn.net/consent?state=" + call.getArgument(0) + "&code_challenge=" + call.getArgument(1));
        when(client.exchange(anyString(), anyString())).thenReturn(new ArchonMemberClient.Tokens("access", "refresh", "Bearer", "profile:read"));
        when(client.userInfo("access")).thenReturn(new ArchonMemberClient.Member("member-7", "003120065"));
        redisValues.clear();
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(call -> redisValues.get(call.getArgument(0)));
        doAnswer(call -> {
            redisValues.put(call.getArgument(0), call.getArgument(1));
            return null;
        }).when(values).set(anyString(), anyString(), any(Duration.class));
        when(redis.delete(anyString())).thenAnswer(call -> redisValues.remove(call.getArgument(0)) != null);
        when(redis.delete(any(java.util.Collection.class))).thenAnswer(call -> {
            java.util.Collection<String> keys = call.getArgument(0);
            return keys.stream().filter(key -> redisValues.remove(key) != null).count();
        });
        logins = new ArchonLoginCache(redis, new ObjectMapper());
        ReflectionTestUtils.setField(logins, "clock", clock);
        deckUserService = mock(DeckUserService.class);
        service = new ApiArchonService(users, logins, client, deckUserService, transactions);
    }

    @AfterEach
    void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @AfterAll
    void close() {
        if (factory != null) { factory.destroy(); }
        if (jdbc != null) { jdbc.execute("SHUTDOWN"); }
    }

    @Test
    void verifiesPkceAndRetainsOnlyTheAccessTokenForOneHour() {
        String url = service.connect().authorizationUrl();
        String state = url.split("state=")[1].split("&")[0];
        var attempt = logins.findAttempt(7);
        assertEquals(DigestUtils.sha256Hex(state), attempt.stateHash());
        assertNotEquals(state, attempt.stateHash());
        assertEquals(2, attempt.authVersion());
        assertEquals(clock.millis() + Duration.ofMinutes(10).toMillis(), attempt.expiresAt());
        assertTrue(url.endsWith(Base64.getUrlEncoder().withoutPadding().encodeToString(DigestUtils.sha256(attempt.verifier()))));
        var status = service.complete(callback(state));
        assertTrue(status.linked());
        assertEquals("003120065", status.veknId());
        verify(deckUserService).refreshTournamentDecks("003120065");
        assertNull(logins.findAttempt(7));
        verify(client).exchange("one-use-code", attempt.verifier());
        verify(values).set("archon:access-token:7", "access", Duration.ofHours(1));
        verify(client, never()).revoke(anyString());
        assertFalse(redisValues.values().stream().anyMatch(value -> value.contains("refresh")));
        assertEquals(400, failure(() -> service.complete(callback(state))));
        verify(client, times(1)).exchange(anyString(), anyString());
    }

    @Test
    void wrongAccountCannotConsumeTheOwnersAuthorization() {
        String state = start();
        authenticate(8);
        assertEquals(400, failure(() -> service.complete(callback(state))));
        assertNotNull(logins.findAttempt(7).verifier());
        authenticate(7);
        assertTrue(service.complete(callback(state)).linked());
    }

    @Test
    void rejectsExpiryAndRevokedAuthenticationBeforeContactingArchon() {
        String state = start();
        ReflectionTestUtils.setField(logins, "clock", Clock.offset(clock, Duration.ofMinutes(10)));
        assertEquals(400, failure(() -> service.complete(callback(state))));
        ReflectionTestUtils.setField(logins, "clock", clock);
        jdbc.update("UPDATE `user` SET auth_version = 3 WHERE id = 7");
        assertEquals(400, failure(() -> service.complete(callback(state))));
        verify(client, never()).exchange(anyString(), anyString());
    }

    @Test
    void denialConsumesTheAttemptWithoutCreatingAConnection() {
        var callback = callback(start());
        callback.setCode(null);
        callback.setError("access_denied");
        assertFalse(service.complete(callback).linked());
        assertNull(logins.findAttempt(7));
        verify(client, never()).exchange(anyString(), anyString());
    }

    @Test
    void rejectsMissingVeknAndRevokesTheTokens() {
        when(client.userInfo("access")).thenReturn(new ArchonMemberClient.Member("member-7", null));
        assertEquals(422, failure(() -> service.complete(callback(start()))));
        assertFalse(service.status().linked());
        assertNull(logins.findAttempt(7).verifier());
        verify(client).revoke("refresh");
        verify(client).revoke("access");
        verifyNoInteractions(deckUserService);
    }

    @Test
    void rejectsDuplicateVeknIdsAndChangingAnExistingLink() {
        service.complete(callback(start()));
        assertEquals(409, failure(() -> service.connect()));
        authenticate(8);
        assertEquals(409, failure(() -> service.complete(callback(start()))));
        when(client.userInfo("access")).thenReturn(new ArchonMemberClient.Member("another-member", "003120065"));
        assertEquals(409, failure(() -> service.complete(callback(start()))));
        assertEquals(1, linkedUsers());
    }

    @Test
    void databaseAlsoEnforcesUniqueness() {
        service.complete(callback(start()));
        assertThrows(Exception.class, () -> new TransactionTemplate(transactions)
                .executeWithoutResult(status -> users.updateVerifiedVeknId(8, "003120065")));
        assertEquals(1, linkedUsers());
    }

    @Test
    void upstreamFailuresDoNotRetryOrLeakResponseBodies() {
        when(client.exchange(anyString(), anyString())).thenThrow(new IllegalStateException("secret upstream response"));
        String state = start();
        var exception = assertThrows(ResponseStatusException.class, () -> service.complete(callback(state)));
        assertEquals(502, exception.getStatusCode().value());
        assertFalse(exception.getMessage().contains("secret"));
        assertEquals(400, failure(() -> service.complete(callback(state))));
        verify(client, times(1)).exchange(anyString(), anyString());
        assertFalse(service.status().linked());
        assertNull(logins.findAttempt(7).verifier());
    }

    @Test
    void disconnectRemovesTheLinkAndCachedTokenEvenWhenRevocationFails() {
        service.complete(callback(start()));
        doThrow(new IllegalStateException("unavailable")).when(client).revoke("access");
        service.disconnect();
        assertFalse(service.status().linked());
        assertTrue(redisValues.isEmpty());
        verify(client).revoke("access");
        verify(deckUserService, times(2)).refreshTournamentDecks("003120065");
    }

    @Test
    void tokenExpiryDoesNotRemoveThePermanentLink() {
        service.complete(callback(start()));
        redisValues.clear();
        assertTrue(service.status().linked());
        assertEquals("003120065", service.status().veknId());
    }

    @Test
    void disconnectCancelsAnInFlightCompletion() throws Exception {
        String state = start();
        var entered = new CountDownLatch(1);
        var resume = new CountDownLatch(1);
        when(client.userInfo("access")).thenAnswer(call -> {
            entered.countDown();
            assertTrue(resume.await(5, TimeUnit.SECONDS));
            return new ArchonMemberClient.Member("member-7", "003120065");
        });
        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<Integer> result = executor.submit(() -> {
                authenticate(7);
                try { return failure(() -> service.complete(callback(state))); }
                finally { SecurityContextHolder.clearContext(); }
            });
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                service.disconnect();
            } finally { resume.countDown(); }
            assertEquals(400, result.get(5, TimeUnit.SECONDS));
        }
        assertEquals(0, linkedUsers());
        assertTrue(redisValues.isEmpty());
        verify(client).revoke("refresh");
    }

    @Test
    void aNewAttemptInvalidatesThePreviousOneAndDisconnectWorksWhenDisabled() {
        String old = start();
        String current = start();
        assertNotEquals(old, current);
        assertEquals(400, failure(() -> service.complete(callback(old))));
        service.complete(callback(current));
        when(client.available()).thenReturn(false);
        assertFalse(service.status().available());
        service.disconnect();
        service.disconnect();
        assertFalse(service.status().linked());
        assertEquals(503, failure(() -> service.connect()));
    }

    private String start() {
        return service.connect().authorizationUrl().split("state=")[1].split("&")[0];
    }

    @Test
    void pendingAuthorizationsNeverProvideAVerifiedIdentity() {
        start();
        assertNotNull(logins.findAttempt(7));
        assertFalse(service.status().linked());
        assertNull(service.status().veknId());
        assertEquals(0, linkedUsers());
    }

    private int linkedUsers() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM `user` WHERE vekn_id IS NOT NULL", Integer.class);
    }

    private ApiArchonCallback callback(String state) {
        var callback = new ApiArchonCallback();
        callback.setState(state);
        callback.setCode("one-use-code");
        return callback;
    }

    private int failure(Runnable action) {
        return assertThrows(ResponseStatusException.class, action::run).getStatusCode().value();
    }

    private void authenticate(int id) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(String.valueOf(id), null));
    }
}
