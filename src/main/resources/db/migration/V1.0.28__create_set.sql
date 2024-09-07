CREATE TABLE `set` (
  `id` int NOT NULL,
  `abbrev` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `release_date` DATE DEFAULT NULL,
  `full_name` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL,
  `company` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL,
  `creation_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `modification_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  FULLTEXT KEY (abbrev)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci