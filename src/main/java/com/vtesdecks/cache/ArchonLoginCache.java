package com.vtesdecks.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/** Temporary OAuth data only. Mutations are serialized by the account lock in ApiArchonService. */
@Component
@RequiredArgsConstructor
public class ArchonLoginCache {
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private Clock clock = Clock.systemUTC();

    public void start(Integer userId, String stateHash, String verifier, long authVersion) {
        saveAttempt(userId, new Attempt(stateHash, verifier, authVersion,
                clock.millis() + Duration.ofMinutes(10).toMillis()));
    }

    public Attempt findAttempt(Integer userId) {
        try {
            String value = redis.opsForValue().get(attemptKey(userId));
            if (value == null) {
                return null;
            }
            Attempt attempt = json.readValue(value, Attempt.class);
            return attempt.expiresAt() > clock.millis() ? attempt : null;
        } catch (DataAccessException | JsonProcessingException exception) {
            throw unavailable();
        }
    }

    public void claim(Integer userId, Attempt attempt) {
        saveAttempt(userId, new Attempt(attempt.stateHash(), null, attempt.authVersion(), attempt.expiresAt()));
    }

    public void forgetAttempt(Integer userId) {
        try {
            redis.delete(attemptKey(userId));
        } catch (DataAccessException exception) {
            throw unavailable();
        }
    }

    public void retainAccessToken(Integer userId, String token) {
        try {
            redis.opsForValue().set(tokenKey(userId), token, Duration.ofHours(1));
        } catch (DataAccessException exception) {
            throw unavailable();
        }
    }

    public String disconnect(Integer userId) {
        try {
            String token = redis.opsForValue().get(tokenKey(userId));
            redis.delete(List.of(attemptKey(userId), tokenKey(userId)));
            return token;
        } catch (DataAccessException exception) {
            throw unavailable();
        }
    }

    private void saveAttempt(Integer userId, Attempt attempt) {
        long remaining = attempt.expiresAt() - clock.millis();
        if (remaining <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "archon_invalid_attempt");
        }
        try {
            redis.opsForValue().set(attemptKey(userId), json.writeValueAsString(attempt), Duration.ofMillis(remaining));
        } catch (DataAccessException | JsonProcessingException exception) {
            throw unavailable();
        }
    }

    private String attemptKey(Integer userId) { return "archon:authorization:" + userId; }
    private String tokenKey(Integer userId) { return "archon:access-token:" + userId; }

    private ResponseStatusException unavailable() {
        // Never include Redis payloads or underlying exceptions containing credentials.
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "archon_unavailable");
    }

    public record Attempt(String stateHash, String verifier, long authVersion, long expiresAt) {
        @Override public String toString() { return "Archon authorization [redacted]"; }
    }
}
