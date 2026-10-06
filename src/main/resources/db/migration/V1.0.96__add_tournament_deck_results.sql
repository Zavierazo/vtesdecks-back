ALTER TABLE `deck`
    ADD COLUMN `event_id` VARCHAR(64) DEFAULT NULL AFTER `url`,
    ADD COLUMN `final_vp` DECIMAL(3, 1) DEFAULT NULL AFTER `event_id`,
    ADD COLUMN `position` INT DEFAULT NULL AFTER `final_vp`;

UPDATE `deck`
SET `event_id` = CASE
    WHEN `url` REGEXP '^https?://(www\\.)?vekn\\.net/event-calendar/event/[0-9]+/?$'
        THEN REGEXP_REPLACE(`url`, '^https?://(www\\.)?vekn\\.net/event-calendar/event/([0-9]+)/?$', '\\2')
    WHEN `url` REGEXP '^https?://archon\\.vekn\\.net/t/[A-Za-z0-9-]+/?$'
        THEN REGEXP_REPLACE(`url`, '^https?://archon\\.vekn\\.net/t/([A-Za-z0-9-]+)/?$', '\\1')
    WHEN `url` REGEXP '^https?://archon\\.vekn\\.net/tournament/[A-Za-z0-9-]+/display\\.html/?$'
        THEN REGEXP_REPLACE(`url`, '^https?://archon\\.vekn\\.net/tournament/([A-Za-z0-9-]+)/display\\.html/?$', '\\1')
    ELSE NULL
END
WHERE `type` = 'TOURNAMENT';

UPDATE `deck`
SET `position` = 1
WHERE `type` = 'TOURNAMENT';
