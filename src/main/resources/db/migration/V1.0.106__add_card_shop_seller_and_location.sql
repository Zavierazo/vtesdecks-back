-- Keep nullable metadata out of the primary key. COALESCE is only used by
-- the unique index so missing printing, language or seller cannot bypass it.
-- One ALTER keeps the original schema intact if existing duplicates conflict.
ALTER TABLE card_shop
    ADD COLUMN country CHAR(2) NULL,
    ADD COLUMN continent CHAR(2) NULL,
    ADD COLUMN seller VARCHAR(255) NULL,
    DROP INDEX card_shop_card_unique,
    ADD UNIQUE KEY card_shop_card_unique (
        card_id,
        platform,
        (COALESCE(`set`, '')),
        (COALESCE(locale, '')),
        (COALESCE(seller, ''))
    );
