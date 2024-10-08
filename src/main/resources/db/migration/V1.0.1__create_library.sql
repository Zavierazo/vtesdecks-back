CREATE TABLE `library` (
  `id` int NOT NULL,
  `name` varchar(250) COLLATE utf8mb4_unicode_ci NOT NULL,
  `aka` varchar(50)  COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `Type` varchar(50) NOT NULL,
  `clan` varchar(50),
  `discipline` varchar(50),
  `pool_cost` int DEFAULT NULL,
  `blood_cost` int DEFAULT NULL,
  `burn` varchar(50),
  `text` text  NOT NULL,
  `flavor` text,
  `set` varchar(250),
  `requirement` varchar(50),
  `banned` varchar(50),
  `artist` varchar(250),
  `capacity` varchar(50),
  `draft` varchar(50),
  `conviction_cost` int DEFAULT NULL,
  `creation_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `modification_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  FULLTEXT KEY (name, aka)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
