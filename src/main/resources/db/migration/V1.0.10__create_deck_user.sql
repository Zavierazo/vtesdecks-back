CREATE TABLE `deck_user` (
  `user` int NOT NULL,
  `deck_id` varchar(250) NOT NULL,
  `rate` int,
  `favorite` boolean NOT NULL DEFAULT 0,
  `modification_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user, deck_id),
  FOREIGN KEY (deck_id) REFERENCES `deck`(id),
  FOREIGN KEY (user) REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `deck_user` (`user`, `deck_id`, `rate`)
SELECT id, deck_id, rate FROM deck_rate;
