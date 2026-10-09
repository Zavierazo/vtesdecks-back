-- V1.0.96 used backslash replacement references, which MySQL stored as literal
-- '2' (VEKN) or '1' (legacy Archon). Strip the known URL prefix/suffix instead
-- of relying on database-specific replacement-group syntax.
-- Repair only those signatures; retain event codes already assigned by importers.
UPDATE `deck`
SET `event_id` = REGEXP_REPLACE(`url`, '^https?://(www[.])?vekn[.]net/event-calendar/event/|/$', ''),
    `modification_date` = `modification_date`
WHERE `type` = 'TOURNAMENT'
  AND `event_id` = '2'
  AND `url` REGEXP '^https?://(www[.])?vekn[.]net/event-calendar/event/[0-9]+/?$';

UPDATE `deck`
SET `event_id` = CASE
    WHEN `url` REGEXP '^https?://archon[.]vekn[.]net/t/[A-Za-z0-9-]+/?$'
        THEN REGEXP_REPLACE(`url`, '^https?://archon[.]vekn[.]net/t/|/$', '')
    ELSE REGEXP_REPLACE(`url`, '^https?://archon[.]vekn[.]net/tournament/|/display[.]html/?$', '')
END,
    `modification_date` = `modification_date`
WHERE `type` = 'TOURNAMENT'
  AND `event_id` = '1'
  AND (`url` REGEXP '^https?://archon[.]vekn[.]net/t/[A-Za-z0-9-]+/?$'
       OR `url` REGEXP '^https?://archon[.]vekn[.]net/tournament/[A-Za-z0-9-]+/display[.]html/?$');
