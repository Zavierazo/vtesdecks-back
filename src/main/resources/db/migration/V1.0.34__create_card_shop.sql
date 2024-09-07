CREATE TABLE `card_shop` (
  `id` int NOT NULL AUTO_INCREMENT,
  `card_id` int NOT NULL,
  `platform` varchar(50) NOT NULL,
  `set` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `link` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL,
  `price` decimal NULL,
  `creation_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `modification_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE(card_id, platform, `set`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX card_shop_card_id_idx ON card_shop(card_id);
CREATE INDEX card_shop_card_id_platform_idx ON card_shop(card_id, platform);