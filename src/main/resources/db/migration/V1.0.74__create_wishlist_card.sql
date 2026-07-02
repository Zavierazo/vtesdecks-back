-- Wishlist: one wishlist per user, public by default, reachable by username
ALTER TABLE `user` ADD COLUMN `wishlist_public_visibility` BOOLEAN NOT NULL DEFAULT true;

CREATE TABLE `wishlist_card` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `user_id` INT NOT NULL,
  `card_id` INT NOT NULL,
  `number` INT NOT NULL DEFAULT 1,
  `priority` TINYINT DEFAULT NULL,
  `set` VARCHAR(64) DEFAULT NULL,
  `condition` VARCHAR(2) DEFAULT NULL,
  `language` VARCHAR(2) DEFAULT NULL,
  `notes` TEXT DEFAULT NULL,
  `creation_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `modification_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
  UNIQUE KEY `uq_wishlist_card` (`user_id`, `card_id`, `set`, `condition`, `language`),
  INDEX `idx_wishlist_card_user_card` (`user_id`, `card_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
