package com.vtesdecks.api.service;

import com.vtesdecks.jpa.entity.UserEntity;
import com.vtesdecks.jpa.repositories.UserRepository;
import com.vtesdecks.model.api.ApiResponse;
import com.vtesdecks.service.MailService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import com.vtesdecks.jpa.repositories.UserEmailActionRepository;
import com.vtesdecks.jpa.entity.UserEmailActionEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class EmailActionService {
    public enum Purpose { PASSWORD_RESET, EMAIL_VERIFICATION }
    private static final SecureRandom RANDOM = new SecureRandom();
    private final UserEmailActionRepository actions;
    private final UserSecurityService security;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final MailService mail;
    @Value("${email-action.reset-ttl:PT30M}")
    private Duration resetTtl = Duration.ofMinutes(30);
    @Value("${email-action.verification-ttl:PT24H}")
    private Duration verificationTtl = Duration.ofHours(24);

    @Transactional
    public boolean sendVerification(Integer userId) {
        UserEntity user = users.findById(userId).orElse(null);
        if (user == null) {
            return false;
        }
        if (Boolean.TRUE.equals(user.getValidated())) {
            return false;
        }
        LocalDateTime now = now();
        if (actions.existsByUserIdAndPurposeAndCreatedAtAfter(userId, Purpose.EMAIL_VERIFICATION.name(), now.minusMinutes(30))) {
            return false;
        }
        mail.sendConfirmationMail(user.getEmail(), issue(user, Purpose.EMAIL_VERIFICATION));
        return true;
    }

    String issue(UserEntity user, Purpose purpose) {
        LocalDateTime now = now();
        Duration ttl = purpose == Purpose.PASSWORD_RESET ? resetTtl : verificationTtl;
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalStateException("Email token expiry must be positive");
        }
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        actions.deleteByUserIdAndPurpose(user.getId(), purpose.name());
        UserEmailActionEntity action = new UserEmailActionEntity();
        action.setTokenHash(hash(token));
        action.setUserId(user.getId());
        action.setPurpose(purpose.name());
        action.setTargetEmail(user.getEmail());
        action.setCreatedAt(now);
        action.setExpiresAt(now.plus(ttl));
        actions.saveAndFlush(action);
        return token;
    }

    @Transactional
    public ApiResponse resetPassword(String token, String password) {
        if (!validPassword(password)) {
            return result(false, "Use at least 8 characters including a number (maximum 72 UTF-8 bytes).");
        }
        UserEntity user = consume(token, Purpose.PASSWORD_RESET);
        if (user == null) {
            return invalidLink();
        }
        user.setPassword(encoder.encode(password));
        users.save(user);
        security.revoke(user);
        return result(true, "Your password has been reset. Please log in using your new password.");
    }

    @Transactional
    public ApiResponse verify(String token) {
        UserEntity user = consume(token, Purpose.EMAIL_VERIFICATION);
        if (user == null) {
            return invalidLink();
        }
        user.setValidated(true);
        users.save(user);
        return result(true, "Email verified. You can now log in.");
    }

    private UserEntity consume(String token, Purpose purpose) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            return null;
        }
        String hash = hash(token);
        var action = actions.findById(hash).orElse(null);
        if (action == null) {
            return null;
        }
        if (!action.getExpiresAt().isAfter(now())) {
            actions.delete(action);
            return null;
        }
        if (!purpose.name().equals(action.getPurpose())) {
            return null;
        }
        UserEntity user = users.findById(action.getUserId()).orElse(null);
        actions.delete(action);
        if (user == null || !user.getEmail().equals(action.getTargetEmail())) {
            return null;
        }
        return user;
    }

    public static boolean validPassword(String password) {
        return password != null && password.length() >= 8 && password.matches("(?s).*\\d.*")
                && password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
    private static ApiResponse invalidLink() { return result(false, "Invalid or expired link. Please request a new email."); }
    private static ApiResponse result(boolean successful, String message) {
        ApiResponse response = new ApiResponse();
        response.setSuccessful(successful);
        response.setMessage(message);
        return response;
    }
}
