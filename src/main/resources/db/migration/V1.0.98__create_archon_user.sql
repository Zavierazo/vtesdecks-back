CREATE TABLE `archon_user` (
    `vekn_id` VARCHAR(64) NOT NULL,
    `archon_user_uid` VARCHAR(36) NOT NULL,
    `name` VARCHAR(255) NOT NULL,
    `alias` VARCHAR(255) DEFAULT NULL,
    `country` VARCHAR(2) DEFAULT NULL,
    `city` VARCHAR(255) DEFAULT NULL,
    `roles` JSON NOT NULL,
    `creation_date` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `modification_date` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`vekn_id`),
    KEY `idx_archon_user_uid` (`archon_user_uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
