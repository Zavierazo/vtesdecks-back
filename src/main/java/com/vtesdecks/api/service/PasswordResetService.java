package com.vtesdecks.api.service;

import com.vtesdecks.jpa.entity.UserEntity;
import com.vtesdecks.jpa.repositories.UserRepository;
import com.vtesdecks.service.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {
    private static final long COOLDOWN_MINUTES = 30;

    private final UserRepository userRepository;
    private final EmailActionService emailActions;
    private final MailService mailService;

    public enum Result {
        SENT,
        COOLDOWN,
        USER_NOT_FOUND
    }

    @Transactional
    public Result request(String email) {
        UserEntity user = userRepository.findByEmail(email);
        return user == null ? Result.USER_NOT_FOUND : request(user);
    }

    @Transactional
    public Result request(UserEntity user) {
        if (user.getForgotPasswordDate() != null
                && !user.getForgotPasswordDate().isBefore(LocalDateTime.now().minusMinutes(COOLDOWN_MINUTES))) {
            log.info("Password reset request is in cooldown userId={}", user.getId());
            return Result.COOLDOWN;
        }

        mailService.sendForgotPasswordMail(user.getEmail(), emailActions.issue(user, EmailActionService.Purpose.PASSWORD_RESET));
        user.setForgotPasswordDate(LocalDateTime.now());
        userRepository.save(user);
        log.info("Password reset email sent userId={}", user.getId());
        return Result.SENT;
    }
}
