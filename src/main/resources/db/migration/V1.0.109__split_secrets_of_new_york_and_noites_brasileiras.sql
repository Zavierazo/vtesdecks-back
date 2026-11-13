-- Split the released printings using the official card IDs from V1.0.104.
START TRANSACTION;

CREATE TEMPORARY TABLE release_printings (
    card_id INT PRIMARY KEY,
    abbrev VARCHAR(4) CHARACTER SET ascii NOT NULL
) ENGINE=InnoDB;

INSERT INTO release_printings (card_id, abbrev) VALUES
    (102366, 'NBr'),
    (102367, 'SoNY'),
    (102368, 'NBr'),
    (102369, 'SoNY'),
    (102370, 'SoNY'),
    (102371, 'NBr'),
    (102372, 'NBr'),
    (102373, 'NBr'),
    (102374, 'SoNY'),
    (102375, 'NBr'),
    (102376, 'SoNY'),
    (102377, 'NBr'),
    (102378, 'SoNY'),
    (102379, 'SoNY'),
    (102380, 'SoNY'),
    (102381, 'NBr'),
    (102382, 'NBr'),
    (102383, 'SoNY'),
    (102384, 'NBr'),
    (102385, 'NBr'),
    (102386, 'NBr'),
    (102387, 'NBr'),
    (102388, 'SoNY'),
    (102389, 'SoNY'),
    (102390, 'SoNY'),
    (102391, 'SoNY'),
    (201787, 'SoNY'),
    (201788, 'SoNY'),
    (201789, 'NBr'),
    (201790, 'NBr'),
    (201791, 'NBr'),
    (201792, 'NBr'),
    (201793, 'SoNY'),
    (201794, 'NBr'),
    (201795, 'NBr'),
    (201796, 'NBr'),
    (201797, 'SoNY'),
    (201798, 'SoNY'),
    (201799, 'SoNY'),
    (201800, 'SoNY'),
    (201801, 'SoNY'),
    (201802, 'SoNY'),
    (201803, 'SoNY'),
    (201804, 'SoNY'),
    (201805, 'NBr'),
    (201806, 'NBr'),
    (201807, 'NBr'),
    (201808, 'NBr'),
    (201809, 'NBr'),
    (201810, 'NBr'),
    (201811, 'NBr'),
    (201812, 'SoNY'),
    (201813, 'SoNY'),
    (201814, 'NBr'),
    (201815, 'SoNY'),
    (201816, 'NBr'),
    (201817, 'NBr'),
    (201818, 'SoNY'),
    (201819, 'SoNY'),
    (201820, 'SoNY'),
    (201821, 'NBr'),
    (201822, 'SoNY'),
    (201823, 'SoNY');

UPDATE deck
SET `set` = 'SoNY', modification_date = modification_date
WHERE id = 'preconstructed-secrets_of_new_york' AND `set` IN ('V5SN', 'V5SN:SoNY');

UPDATE deck
SET `set` = 'NBr', modification_date = modification_date
WHERE id = 'preconstructed-noites_brasileiras' AND `set` IN ('V5SN', 'V5SN:NBr');

-- Retain the existing timestamps and let the collection update trigger run normally.
UPDATE collection_card c JOIN release_printings p ON p.card_id = c.card_id
SET c.`set` = p.abbrev, c.modification_date = c.modification_date
WHERE c.`set` = 'V5SN' OR c.`set` LIKE 'V5SN:%';

UPDATE collection_card_history h JOIN release_printings p ON p.card_id = h.card_id
SET h.`set` = p.abbrev
WHERE h.`set` = 'V5SN' OR h.`set` LIKE 'V5SN:%';

UPDATE wishlist_card w JOIN release_printings p ON p.card_id = w.card_id
SET w.`set` = p.abbrev, w.modification_date = w.modification_date
WHERE w.`set` = 'V5SN' OR w.`set` LIKE 'V5SN:%';

UPDATE limited_format
SET `format` = JSON_REMOVE(JSON_INSERT(`format`,
        '$.sets.SoNY', JSON_EXTRACT(`format`, '$.sets.V5SN'),
        '$.sets.NBr', JSON_EXTRACT(`format`, '$.sets.V5SN')), '$.sets.V5SN'),
    modification_date = modification_date
WHERE JSON_CONTAINS_PATH(`format`, 'one', '$.sets.V5SN');

UPDATE deck
SET extra = JSON_REMOVE(JSON_INSERT(extra,
        '$.limitedFormat.sets.SoNY', JSON_EXTRACT(extra, '$.limitedFormat.sets.V5SN'),
        '$.limitedFormat.sets.NBr', JSON_EXTRACT(extra, '$.limitedFormat.sets.V5SN')), '$.limitedFormat.sets.V5SN'),
    modification_date = modification_date
WHERE JSON_CONTAINS_PATH(extra, 'one', '$.limitedFormat.sets.V5SN');

-- Search preset parameters are comma-separated strings, including the older singular key.
SET @release_set_filter = '(^|(?<=,))[[:space:]]*V5SN[[:space:]]*(?=,|$)';

UPDATE user_search_preset
SET params = JSON_SET(params, '$.sets', REGEXP_REPLACE(params->>'$.sets', @release_set_filter, 'SoNY,NBr')),
    modification_date = modification_date
WHERE REGEXP_LIKE(params->>'$.sets', @release_set_filter);

UPDATE user_search_preset
SET params = JSON_SET(params, '$.notSets', REGEXP_REPLACE(params->>'$.notSets', @release_set_filter, 'SoNY,NBr')),
    modification_date = modification_date
WHERE REGEXP_LIKE(params->>'$.notSets', @release_set_filter);

UPDATE user_search_preset
SET params = JSON_SET(params, '$.set', REGEXP_REPLACE(params->>'$.set', @release_set_filter, 'SoNY,NBr')),
    modification_date = modification_date
WHERE REGEXP_LIKE(params->>'$.set', @release_set_filter);

-- Reload both card CSVs through the startup importer with the new set mapping.
DELETE FROM load_history WHERE script IN ('data/vtescrypt.csv', 'data/vteslib.csv');

DROP TEMPORARY TABLE release_printings;
COMMIT;

SET @release_set_filter = NULL;
