-- Minimum card price in the default currency (EUR), computed with the same rules as the
-- price displayed to users (enabled platforms, in-stock offers preferred, currency converted).
-- Kept in sync by the card caches on every refresh and used for price sorting in queries.
CREATE TABLE `card_min_price` (
  `card_id` INT NOT NULL,
  `min_price` DECIMAL(10,2) DEFAULT NULL,
  `creation_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `modification_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`card_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
