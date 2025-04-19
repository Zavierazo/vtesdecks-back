CREATE TABLE `user_ai_ask` (
  `id` int NOT NULL AUTO_INCREMENT,
  `user` varchar(250) NOT NULL,
  `question` varchar(2000) NOT NULL,
  `answer` text NOT NULL,
  `creation_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX user_ai_ask_user_idx ON user_ai_ask(user);