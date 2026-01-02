CREATE TABLE `card_errata` (
  `id` int NOT NULL AUTO_INCREMENT,
  `card_id` int NOT NULL,
  `effective_date` date NOT NULL,
  `description` text NOT NULL,
  `requires_warning` boolean NOT NULL DEFAULT false,
  `creation_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `modification_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX idx_card_id (card_id),
  INDEX idx_warning_card_date (requires_warning, card_id, effective_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;