package com.vtesdecks.scheduler;

import com.vtesdecks.jpa.repositories.UserNotificationRepository;
import com.vtesdecks.enums.UserNotificationType;
import com.vtesdecks.jpa.entity.UserNotificationEntity;
import com.vtesdecks.service.push.WebPushDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PatreonReminderScheduler {

    private static final int REMINDER_MONTHS = 6;

    private final UserNotificationRepository userNotificationRepository;
    private final WebPushDeliveryService webPushDeliveryService;

    @Scheduled(cron = "${jobs.patreonReminderCron:0 0 4 * * *}")
    @Transactional
    public void remindPatreon() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.minusMonths(REMINDER_MONTHS);
        List<UserNotificationEntity> reminders = userNotificationRepository
                .findByTypeAndLinkContainingAndCreationDateBefore(UserNotificationType.LINK, "patreon", threshold);
        reminders.forEach(reminder -> {
            reminder.setCreationDate(now);
            reminder.setRead(false);
        });
        if (!reminders.isEmpty()) {
            userNotificationRepository.saveAll(reminders);
            reminders.forEach(webPushDeliveryService::deliver);
            log.info("Refreshed {} Patreon reminder notifications older than {} months", reminders.size(), REMINDER_MONTHS);
        }
    }
}
