CREATE TABLE `deck_rate` (
  `id` int NOT NULL,
  `deck_id` varchar(250) NOT NULL,
  `rate` int NOT NULL,
  `modification_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id, deck_id),
  FOREIGN KEY (deck_id) REFERENCES `deck`(id),
  FOREIGN KEY (id) REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
