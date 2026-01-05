CREATE TABLE `user_follower` (
  `user_id` int NOT NULL,
  `followed_id` int NOT NULL,
  `creation_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, followed_id),
  FOREIGN KEY (user_id) REFERENCES `user`(id) ON DELETE CASCADE,
  FOREIGN KEY (followed_id) REFERENCES `user`(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

