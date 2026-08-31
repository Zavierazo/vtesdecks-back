CREATE TABLE `user_search_preset` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `user_id` INT NOT NULL,
  `client_id` VARCHAR(36) DEFAULT NULL,
  `scope` VARCHAR(16) NOT NULL,
  `name` VARCHAR(64) NOT NULL COLLATE utf8mb4_0900_as_ci,
  `params` JSON NOT NULL,
  `creation_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `modification_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_user_search_preset_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  UNIQUE KEY `uq_user_search_preset_name` (`user_id`, `scope`, `name`),
  UNIQUE KEY `uq_user_search_preset_client` (`user_id`, `client_id`),
  KEY `idx_user_search_preset_user` (`user_id`, `scope`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
