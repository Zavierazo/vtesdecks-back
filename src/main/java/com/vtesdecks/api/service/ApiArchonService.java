package com.vtesdecks.api.service;

import com.vtesdecks.api.util.ApiUtils;
import com.vtesdecks.cache.ArchonLoginCache;
import com.vtesdecks.cache.ArchonLoginCache.Attempt;
import com.vtesdecks.integration.ArchonMemberClient;
import com.vtesdecks.jpa.repositories.UserRepository;
import com.vtesdecks.model.api.ApiArchonAuthorization;
import com.vtesdecks.model.api.ApiArchonCallback;
import com.vtesdecks.model.api.ApiArchonStatus;
import com.vtesdecks.service.DeckUserService;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

@Service
public class ApiArchonService {
    private final UserRepository users;
    private final ArchonLoginCache logins;
    private final ArchonMemberClient client;
    private final DeckUserService deckUserService;
    private final TransactionTemplate transaction;
    private final SecureRandom random = new SecureRandom();

    public ApiArchonService(UserRepository users, ArchonLoginCache logins,
                            ArchonMemberClient client, DeckUserService deckUserService,
                            PlatformTransactionManager transactionManager) {
        this.users = users;
        this.logins = logins;
        this.client = client;
        this.deckUserService = deckUserService;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public ApiArchonStatus status() {
        String veknId = users.findVerifiedVeknId(userId());
        return new ApiArchonStatus(client.available(), veknId != null, veknId);
    }

    public ApiArchonAuthorization connect() {
        requireAvailable();
        Integer userId = userId();
        String state = randomValue(32);
        String verifier = randomValue(64);
        transaction.executeWithoutResult(status -> {
            var user = lockUser(userId);
            if (user.getVeknId() != null) {
                throw error(HttpStatus.CONFLICT, "archon_already_linked");
            }
            logins.start(userId, DigestUtils.sha256Hex(state), verifier, user.getAuthVersion());
        });
        String challenge = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(DigestUtils.sha256(verifier.getBytes(StandardCharsets.US_ASCII)));
        return new ApiArchonAuthorization(client.authorizationUrl(state, challenge));
    }

    public ApiArchonStatus complete(ApiArchonCallback callback) {
        requireAvailable();
        validateCallback(callback);
        Integer userId = userId();
        var attempt = transaction.execute(status -> consume(userId, callback));
        if (attempt == null) {
            return status();
        }
        ArchonMemberClient.Tokens tokens = null;
        boolean retained = false;
        try {
            tokens = client.exchange(callback.getCode(), attempt.verifier());
            if (tokens == null || StringUtils.isBlank(tokens.accessToken())
                    || !"Bearer".equalsIgnoreCase(tokens.tokenType()) || tokens.scope() == null
                    || !Arrays.asList(tokens.scope().split("\\s+")).contains("profile:read")) {
                throw error(HttpStatus.BAD_GATEWAY, "archon_unavailable");
            }
            var member = client.userInfo(tokens.accessToken());
            if (member == null || StringUtils.isBlank(member.sub()) || member.sub().length() > 36) {
                throw error(HttpStatus.BAD_GATEWAY, "archon_unavailable");
            }
            String veknId = StringUtils.trimToNull(member.veknId());
            if (veknId == null || veknId.length() > 64 || !veknId.matches("[0-9]+")) {
                throw error(HttpStatus.UNPROCESSABLE_ENTITY, "archon_vekn_missing");
            }
            String accessToken = tokens.accessToken();
            transaction.executeWithoutResult(status -> {
                var user = lockUser(userId);
                var current = logins.findAttempt(userId);
                if (user.getAuthVersion() != attempt.authVersion() || current == null || current.verifier() != null
                        || !Objects.equals(current.stateHash(), attempt.stateHash())) {
                    throw error(HttpStatus.BAD_REQUEST, "archon_invalid_attempt");
                }
                if (user.getVeknId() != null) {
                    throw error(HttpStatus.CONFLICT, "archon_already_linked");
                }
                users.updateVerifiedVeknId(userId, veknId);
                logins.retainAccessToken(userId, accessToken);
                logins.forgetAttempt(userId);
                deckUserService.refreshTournamentDecks(veknId);
            });
            retained = true;
            return status();
        } catch (DataIntegrityViolationException exception) {
            throw error(HttpStatus.CONFLICT, "archon_identity_in_use");
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            // Upstream response bodies may contain credentials; do not log or return them.
            throw error(HttpStatus.BAD_GATEWAY, "archon_unavailable");
        } finally {
            if (tokens != null && !retained) {
                revoke(tokens.refreshToken());
                revoke(tokens.accessToken());
            }
        }
    }

    public void disconnect() {
        Integer userId = userId();
        String token = transaction.execute(status -> {
            var user = lockUser(userId);
            String cachedToken = logins.disconnect(userId);
            users.updateVerifiedVeknId(userId, null);
            deckUserService.refreshTournamentDecks(user.getVeknId());
            return cachedToken;
        });
        revoke(token);
    }

    private Attempt consume(Integer userId, ApiArchonCallback callback) {
        var user = lockUser(userId);
        var attempt = logins.findAttempt(userId);
        if (attempt == null || attempt.verifier() == null || attempt.authVersion() != user.getAuthVersion()
                || !Objects.equals(attempt.stateHash(), DigestUtils.sha256Hex(callback.getState()))) {
            throw error(HttpStatus.BAD_REQUEST, "archon_invalid_attempt");
        }
        if (StringUtils.isNotBlank(callback.getError())) {
            logins.forgetAttempt(userId);
            return null;
        }
        logins.claim(userId, attempt);
        return attempt;
    }

    private void validateCallback(ApiArchonCallback callback) {
        if (callback == null || callback.getState() == null || !callback.getState().matches("[A-Za-z0-9_-]{43}")
                || (StringUtils.isBlank(callback.getCode()) == StringUtils.isBlank(callback.getError()))
                || (callback.getCode() != null && callback.getCode().length() > 2048)
                || (callback.getError() != null && callback.getError().length() > 128)) {
            throw error(HttpStatus.BAD_REQUEST, "archon_invalid_attempt");
        }
    }

    private UserRepository.ArchonAccountLock lockUser(Integer userId) {
        return users.lockArchonAccount(userId)
                .orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, "archon_session_changed"));
    }

    private Integer userId() {
        Integer userId = ApiUtils.extractUserId();
        if (userId == null) {
            throw error(HttpStatus.UNAUTHORIZED, "archon_session_changed");
        }
        return userId;
    }

    private void requireAvailable() {
        if (!client.available()) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "archon_unavailable");
        }
    }

    private void revoke(String token) {
        if (StringUtils.isBlank(token)) {
            return;
        }
        try {
            client.revoke(token);
        } catch (Exception ignored) {
            // Best effort. Redis expiration and the permanent VEKN link remain independent.
        }
    }

    private String randomValue(int length) {
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private ResponseStatusException error(HttpStatus status, String reason) {
        return new ResponseStatusException(status, reason);
    }
}
