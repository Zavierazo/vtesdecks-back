CREATE TABLE `deck` (
  `id` varchar(250) NOT NULL,
  `tournament` varchar(250),
  `players` int,
  `year` int NOT NULL,
  `author` varchar(250) NOT NULL,
  `url` varchar(250),
  `source` varchar(250),
  `name` varchar(250) NOT NULL,
  `description` varchar(10000),
  `verified` boolean NOT NULL,
  `views` int NOT NULL,
  `creation_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `modification_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  FULLTEXT KEY (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
