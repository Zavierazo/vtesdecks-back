CREATE TABLE `user_push_subscription` (
  `id` int NOT NULL AUTO_INCREMENT,
  `user` int NOT NULL,
  `endpoint` varchar(2048) NOT NULL,
  `endpoint_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `p256dh` varchar(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `auth` varchar(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `expiration_time` timestamp(3) NULL,
  `creation_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `modification_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_push_subscription_endpoint_hash` (`endpoint_hash`),
  KEY `idx_user_push_subscription_user` (`user`),
  CONSTRAINT `fk_user_push_subscription_user`
    FOREIGN KEY (`user`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
