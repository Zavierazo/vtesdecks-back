CREATE TABLE `deck_draft` (
  `id` varchar(250) NOT NULL,
  `user` int NOT NULL,
  `content` JSON,
  `modification_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id, user),
  FOREIGN KEY (user) REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
