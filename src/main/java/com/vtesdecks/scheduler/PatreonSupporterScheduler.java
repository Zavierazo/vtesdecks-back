package com.vtesdecks.scheduler;

import com.vtesdecks.api.service.PatreonSupporterService;
import com.vtesdecks.api.service.PatreonMembershipService;
import feign.FeignException;
import com.vtesdecks.integration.PatreonMember.Decision;
import com.vtesdecks.jpa.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
@Slf4j
public class PatreonSupporterScheduler {
    private final PatreonMembershipService client;
    private final UserRepository users;
    private final PatreonSupporterService supporters;

    @Scheduled(cron = "${jobs.patreonSupportersCron:0 30 5 * * *}", zone = "Europe/Madrid")
    public void scheduled() {
        try {
            reconcile();
        } catch (Exception exception) {
            // Provider errors may contain credentials or private responses. Log only their type/status.
            String reason = exception instanceof FeignException response
                    ? "HTTP " + response.status() : exception.getClass().getSimpleName();
            log.error("Patreon supporter reconciliation failed ({}); check API credentials and campaign", reason);
        }
    }

    public synchronized void reconcile() {
        if (!client.available()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Patreon API is not configured");
        }
        // Capture only already-linked accounts. Never match by email or link accounts in this job.
        var accounts = users.findPatreonAccounts();
        var members = client.members();
        int granted = 0;
        int removed = 0;
        int unchanged = 0;
        for (var account : accounts) {
            if (StringUtils.isBlank(account.getPatreonId())) {
                continue;
            }
            var member = members.get(account.getPatreonId());
            Decision decision = member == null ? Decision.REMOVE : member.decision();
            int changed = supporters.apply(account.getId(), account.getPatreonId(), decision);
            if (changed == 0) {
                unchanged++;
            } else if (decision == Decision.GRANT) {
                granted++;
            } else {
                removed++;
            }
        }
        log.info("Patreon supporter reconciliation completed linked={} granted={} removed={} unchanged={}",
                accounts.size(), granted, removed, unchanged);
    }
}
