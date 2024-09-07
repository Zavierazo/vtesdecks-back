CREATE TABLE `comment` (
  `id` int NOT NULL AUTO_INCREMENT,
  `user` int NOT NULL,
  `parent` int,
  `page_identifier` varchar(250) NOT NULL,
  `content` varchar(2000) NOT NULL,
  `deleted` boolean default false,
  `creation_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `modification_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  FOREIGN KEY (parent) REFERENCES `comment`(id),
  FOREIGN KEY (user) REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
