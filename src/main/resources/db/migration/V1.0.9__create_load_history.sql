CREATE TABLE `load_history` (
  `script` varchar(200) NOT NULL,
  `checksum` varchar(200) NOT NULL,
  `execution_time` long NOT NULL,
  `modification_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
   PRIMARY KEY (script)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
