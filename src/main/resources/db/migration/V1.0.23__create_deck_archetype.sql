CREATE TABLE `deck_archetype` (
  `id` int NOT NULL,
  `name` varchar(250) COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` varchar(50) NOT NULL,
  `description` text  NOT NULL,
  `url` varchar(250) NOT NULL,
  `source` varchar(250),
  `modification_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
