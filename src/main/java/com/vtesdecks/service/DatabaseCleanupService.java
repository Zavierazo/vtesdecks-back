package com.vtesdecks.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DatabaseCleanupService {
    public static final int BATCH_SIZE = 500;

    private static final String COMMENT_ELIGIBLE = """
            (c.deleted = true AND c.modification_date < ?)
            OR (
                LEFT(c.page_identifier, 5) = 'deck_'
                AND NOT EXISTS (
                    SELECT 1 FROM deck d WHERE d.id = SUBSTRING(c.page_identifier, 6)
                )
            )
            """;

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public int deleteOldReadNotificationsBatch(LocalDateTime cutoff) {
        return jdbcTemplate.update("""
                DELETE FROM user_notification
                WHERE `read` = true
                  AND creation_date < ?
                  AND NOT (type = 'LINK' AND COALESCE(link, '') LIKE '%patreon%')
                LIMIT ?
                """, Timestamp.valueOf(cutoff), BATCH_SIZE);
    }

    @Transactional
    public int deleteEligibleCommentsBatch(LocalDateTime cutoff) {
        List<Integer> ids = jdbcTemplate.queryForList("""
                SELECT c.id
                FROM comment c
                WHERE (
                """ + COMMENT_ELIGIBLE + """
                )
                  AND NOT EXISTS (SELECT 1 FROM comment child WHERE child.parent = c.id)
                ORDER BY c.id
                LIMIT ?
                """, Integer.class, Timestamp.valueOf(cutoff), BATCH_SIZE);
        if (ids.isEmpty()) {
            return 0;
        }

        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        Object[] deleteParameters = new Object[ids.size() + 1];
        System.arraycopy(ids.toArray(), 0, deleteParameters, 0, ids.size());
        deleteParameters[ids.size()] = Timestamp.valueOf(cutoff);
        int deleted = jdbcTemplate.update("""
                DELETE c
                FROM comment c
                WHERE c.id IN (
                """ + placeholders + """
                ) AND (
                """ + COMMENT_ELIGIBLE + """
                )
                """, deleteParameters);

        Object[] notificationIds = ids.stream().map(String::valueOf).toArray();
        jdbcTemplate.update("""
                DELETE n
                FROM user_notification n
                WHERE n.type = 'COMMENT'
                  AND n.reference_id IN (
                """ + placeholders + """
                  )
                  AND NOT EXISTS (
                      SELECT 1 FROM comment c
                      WHERE BINARY CAST(c.id AS CHAR) = BINARY n.reference_id
                  )
                """, notificationIds);
        return deleted;
    }

    public long countBlockedEligibleComments(LocalDateTime cutoff) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM comment c
                WHERE (
                """ + COMMENT_ELIGIBLE + """
                )
                  AND EXISTS (SELECT 1 FROM comment child WHERE child.parent = c.id)
                """, Long.class, Timestamp.valueOf(cutoff));
        return count == null ? 0 : count;
    }

    @Transactional
    public int deleteOrphanReactionsBatch() {
        return jdbcTemplate.update("""
                DELETE FROM reaction
                WHERE (
                    target_type = 'DECK'
                    AND NOT EXISTS (SELECT 1 FROM deck d WHERE d.id = reaction.target_id)
                ) OR (
                    target_type = 'COMMENT'
                    AND NOT EXISTS (
                        SELECT 1 FROM comment c
                        WHERE BINARY CAST(c.id AS CHAR) = BINARY reaction.target_id
                    )
                )
                LIMIT ?
                """, BATCH_SIZE);
    }
}
