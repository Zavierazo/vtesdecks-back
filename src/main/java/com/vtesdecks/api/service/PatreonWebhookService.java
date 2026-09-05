package com.vtesdecks.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vtesdecks.jpa.entity.UserEntity;
import com.vtesdecks.jpa.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
public class PatreonWebhookService {

    private static final String SUPPORTER_ROLE = "supporter";
    private static final String ASSIGN_SUPPORTER_SQL = """
            INSERT IGNORE INTO user_role (user_id, role_id)
            SELECT ?, MIN(candidate_role.id)
            FROM role candidate_role
            WHERE candidate_role.name = ?
              AND NOT EXISTS (
                SELECT 1
                FROM user_role assigned_user_role
                JOIN role assigned_role ON assigned_role.id = assigned_user_role.role_id
                WHERE assigned_user_role.user_id = ? AND assigned_role.name = ?
              )
            HAVING MIN(candidate_role.id) IS NOT NULL
            """;

    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void process(byte[] body) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        JsonNode data = root.path("data");
        if (data.isMissingNode() || !data.isObject()) {
            throw new IllegalArgumentException("Missing Patreon member data");
        }

        String memberId = text(data, "id");
        JsonNode attributes = data.path("attributes");
        long lifetimeSupportCents = attributes.path("campaign_lifetime_support_cents").asLong(0);
        if (lifetimeSupportCents <= 0) {
            // Patreon webhook examples have historically used both names.
            lifetimeSupportCents = attributes.path("lifetime_support_cents").asLong(0);
        }
        if (lifetimeSupportCents <= 0) {
            log.info("Ignoring unpaid Patreon member {}", memberId);
            return;
        }

        String email = text(attributes, "email");
        if (StringUtils.isBlank(email)) {
            email = includedUserEmail(root, data);
        }
        if (StringUtils.isBlank(email)) {
            log.info("Ignoring paid Patreon member {} because no email was supplied", memberId);
            return;
        }

        UserEntity user = userRepository.findByEmailIgnoreCase(email.trim());
        if (user == null) {
            log.info("No VTESDecks user matches paid Patreon member {} with email {}", memberId, email);
            return;
        }

        int inserted = jdbcTemplate.update(
                ASSIGN_SUPPORTER_SQL,
                user.getId(), SUPPORTER_ROLE, user.getId(), SUPPORTER_ROLE);
        if (inserted > 0) {
            log.info("Granted supporter role to VTESDecks user {} from Patreon member {}", user.getId(), memberId);
        } else {
            log.debug("VTESDecks user {} already has the supporter role", user.getId());
        }
    }

    private String includedUserEmail(JsonNode root, JsonNode data) {
        String userId = text(data.path("relationships").path("user").path("data"), "id");
        if (StringUtils.isBlank(userId) || !root.path("included").isArray()) {
            return null;
        }
        for (JsonNode included : root.path("included")) {
            if ("user".equals(text(included, "type")) && userId.equals(text(included, "id"))) {
                return text(included.path("attributes"), "email");
            }
        }
        return null;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.textValue() : null;
    }
}
