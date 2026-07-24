-- Price converted to the default currency (EUR), kept in sync on every card_shop write
-- and used to sort by price consistently with the price displayed to users.
ALTER TABLE `card_shop` ADD COLUMN `price_default_currency` DECIMAL(10,2) DEFAULT NULL AFTER `currency`;

-- Rows already in the default currency need no conversion; the rest are backfilled on startup.
UPDATE `card_shop` SET `price_default_currency` = `price` WHERE `currency` = 'EUR';
