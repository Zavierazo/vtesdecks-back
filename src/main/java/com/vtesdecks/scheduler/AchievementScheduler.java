package com.vtesdecks.scheduler;

import com.vtesdecks.api.service.AchievementService;
import com.vtesdecks.jpa.entity.UserEntity;
import com.vtesdecks.jpa.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AchievementScheduler {
    private final AchievementService achievementService;
    private final UserRepository userRepository;

    @Scheduled(cron = "${jobs.achievementCron:0 20 * * * *}")
    public void reconcile() {
        int processed = 0;
        int pageNumber = 0;
        Page<UserEntity> page;
        do {
            page = userRepository.findAll(PageRequest.of(pageNumber++, 100));
            for (UserEntity user : page.getContent()) {
                try {
                    achievementService.evaluateUser(user.getId(), true, true);
                    processed++;
                } catch (RuntimeException e) {
                    log.error("Could not reconcile achievements for user {}", user.getId(), e);
                }
            }
        } while (page.hasNext());
        log.info("Achievement reconciliation completed for {} users", processed);
    }
}
