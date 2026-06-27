package com.vtesdecks.scheduler;

import com.vtesdecks.jpa.repositories.UserNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class PatreonReminderScheduler {

    private static final int REMINDER_MONTHS = 6;

    private final UserNotificationRepository userNotificationRepository;

    @Scheduled(cron = "${jobs.patreonReminderCron:0 0 4 * * *}")
    @Transactional
    public void remindPatreon() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.minusMonths(REMINDER_MONTHS);
        int refreshed = userNotificationRepository.refreshOldPatreonNotifications(now, threshold);
        if (refreshed > 0) {
            log.info("Refreshed {} Patreon reminder notifications older than {} months", refreshed, REMINDER_MONTHS);
        }
    }
}
