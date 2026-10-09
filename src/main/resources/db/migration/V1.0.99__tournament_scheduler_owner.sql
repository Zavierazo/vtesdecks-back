ALTER TABLE `deck`
    ADD COLUMN `scheduler_owner` VARCHAR(32) DEFAULT NULL AFTER `source`;

CREATE INDEX `idx_deck_tournament_result` ON `deck` (`type`, `event_id`, `position`, `deleted`);

-- Source is authoritative; the attribution is used only when neither source matches.
-- Explicitly retain modification_date, including on schemas with ON UPDATE timestamps.
UPDATE `deck`
SET `scheduler_owner` = CASE
    WHEN TRIM(`source`) LIKE 'http://www.vekn.fr/decks/twd.htm#%'
      OR TRIM(`source`) LIKE 'https://www.vekn.fr/decks/twd.htm#%' THEN 'TWDA'
    WHEN TRIM(`source`) LIKE 'https://archon.vekn.net/tournaments/%'
      OR TRIM(`source`) LIKE 'http://archon.vekn.net/tournaments/%' THEN 'ARCHON'
    WHEN LOCATE('Imported via <a href="https://github.com/gurchon-hall/channel-ten/tree/main" target="_blank" rel="noopener">channel-ten</a> by Lyon Martin.', `description`) > 0
        THEN 'ETERNAL_VIGILANCE'
    ELSE NULL
END,
    `modification_date` = `modification_date`
WHERE `type` = 'TOURNAMENT';
