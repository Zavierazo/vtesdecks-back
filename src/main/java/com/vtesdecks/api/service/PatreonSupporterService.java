package com.vtesdecks.api.service;

import com.vtesdecks.integration.PatreonMember.Decision;
import com.vtesdecks.jpa.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PatreonSupporterService {
    private static final String ASSIGN_SQL = """
            INSERT IGNORE INTO user_role (user_id, role_id)
            SELECT ?, MIN(candidate_role.id) FROM role candidate_role
            WHERE candidate_role.name = 'supporter'
              AND NOT EXISTS (SELECT 1 FROM user_role ur JOIN role r ON r.id = ur.role_id
                              WHERE ur.user_id = ? AND r.name = 'supporter')
            HAVING MIN(candidate_role.id) IS NOT NULL
            """;
    private static final String REMOVE_SQL = """
            DELETE FROM user_role WHERE user_id = ? AND role_id IN
                (SELECT id FROM role WHERE name = 'supporter')
            """;
    private final UserRepository users;
    private final JdbcTemplate jdbc;

    @Transactional
    public int apply(Integer userId, String expectedPatreonId, Decision decision) {
        if (StringUtils.isBlank(expectedPatreonId) || decision == Decision.KEEP) {
            return 0;
        }
        var account = users.lockPatreonAccount(userId).orElse(null);
        // Recheck the live identity under lock: an admin may have cleared or replaced it.
        if (account == null || !expectedPatreonId.equals(account.getPatreonId())) {
            return 0;
        }
        int changed;
        if (decision == Decision.GRANT) {
            changed = jdbc.update(ASSIGN_SQL, userId, userId);
        } else {
            changed = jdbc.update(REMOVE_SQL, userId);
        }
        if (changed > 0) {
            log.info("Patreon supporter changed userId={} decision={}", userId, decision);
        }
        return changed;
    }
}
