CREATE TABLE `deck_card` (
  `deck_id` varchar(250) NOT NULL,
  `id` int NOT NULL,
  `number` int NOT NULL,
  `creation_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `modification_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (deck_id,id),
  FOREIGN KEY (deck_id) REFERENCES `deck`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
