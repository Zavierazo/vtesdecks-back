CREATE TABLE `user` (
  `id` int NOT NULL AUTO_INCREMENT,
  `username` varchar(250) NOT NULL,
  `email` varchar(250) NOT NULL,
  `password` varchar(250) NOT NULL,
  `login_hash` varchar(250) NOT NULL,
  `profile_image` varchar(250),
  `validated` boolean NOT NULL,
  `admin` boolean NOT NULL,
  PRIMARY KEY (id),
  UNIQUE(username),
  UNIQUE(email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
