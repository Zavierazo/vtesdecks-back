CREATE TABLE IF NOT EXISTS `reaction` (
  `user` int NOT NULL,
  `target_type` VARCHAR(10) NOT NULL,
  `target_id` VARCHAR(250) NOT NULL,
  `reaction` VARCHAR(32) NOT NULL,
  `creation_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`user`, `target_type`, `target_id`, `reaction`),
  INDEX `idx_reaction_target` (`target_type`, `target_id`),
  FOREIGN KEY (`user`) REFERENCES `user`(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
