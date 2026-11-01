package com.vtesdecks.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vtesdecks.configuration.PatreonWebhookConfiguration;
import com.vtesdecks.integration.PatreonMember;
import com.vtesdecks.integration.PatreonMember.Decision;
import com.vtesdecks.jpa.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
public class PatreonWebhookService {
    private final ObjectMapper objectMapper;
    private final UserRepository users;
    private final PatreonSupporterService supporters;
    private final PatreonWebhookConfiguration configuration;

    @Transactional
    public void process(String event, byte[] body) throws IOException {
        if (StringUtils.isBlank(configuration.getCampaignId())) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Patreon campaign is not configured");
        }
        JsonNode root = objectMapper.readTree(body);
        if (root == null || !root.path("data").isObject()) {
            throw new IllegalArgumentException("Missing Patreon member data");
        }
        JsonNode data = root.path("data");
        String campaignId = data.path("relationships").path("campaign").path("data").path("id").asText();
        if (!configuration.getCampaignId().equals(campaignId)) {
            log.warn("Ignored Patreon webhook for another or missing campaign");
            return;
        }
        PatreonMember member = PatreonMember.read(root, data, configuration.getCampaignId(), "members:delete".equals(event));
        Integer userId = users.findUserIdByPatreonId(member.userId()).orElse(null);
        if (userId == null && member.decision() == Decision.GRANT && member.email() != null) {
            var emailUser = users.findByEmailIgnoreCase(member.email());
            if (emailUser != null) {
                var account = users.lockPatreonAccount(emailUser.getId()).orElseThrow();
                if (StringUtils.isNotBlank(account.getPatreonId()) && !member.userId().equals(account.getPatreonId())) {
                    log.warn("Patreon identity conflict userId={} receivedPatreonId={}", account.getId(), member.userId());
                    return;
                }
                users.updatePatreonId(account.getId(), member.userId());
                userId = account.getId();
                log.info("Linked Patreon account userId={} patreonId={}", userId, member.userId());
            }
        }
        if (userId == null) {
            log.info("No linked account for Patreon userId={} decision={}", member.userId(), member.decision());
            return;
        }
        supporters.apply(userId, member.userId(), member.decision());
    }
}
