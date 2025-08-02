CREATE TABLE `collection_binder` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `collection_id` INT NOT NULL,
  `name` VARCHAR(100) NOT NULL,
  `description` TEXT DEFAULT NULL,
  `icon` VARCHAR(100) DEFAULT NULL,
  `public_visibility` tinyint(1) NOT NULL DEFAULT '0',
  `public_hash` varchar(20) DEFAULT NULL,
  `creation_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `modification_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  FOREIGN KEY (`collection_id`) REFERENCES `collection`(`id`) ON DELETE CASCADE,
  UNIQUE KEY `uq_collection_binder_collection_id_name` (`collection_id`, `name`)
);