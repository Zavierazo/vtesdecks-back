CREATE TABLE `user_notification` (
  `id` int NOT NULL AUTO_INCREMENT,
  `user` int NOT NULL,
  `reference_id` int NOT NULL,
  `read` boolean NOT NULL,
  `type` varchar(10) NOT NULL,
  `notification` varchar(1000) NOT NULL,
  `link` varchar(1000) NOT NULL,
  `creation_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  FOREIGN KEY (user) REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX user_notification_user_idx ON user_notification(user);
CREATE INDEX user_notification_reference_id_idx ON user_notification(reference_id);