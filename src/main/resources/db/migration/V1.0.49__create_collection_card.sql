CREATE TABLE `collection_card` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `collection_id` INT NOT NULL,
  `card_id` INT NOT NULL,
  `set` varchar(64) DEFAULT NULL,
  `number` INT NOT NULL,
  `binder_id` INT DEFAULT NULL,
  `condition` VARCHAR(2) DEFAULT NULL,
  `language` VARCHAR(2) DEFAULT NULL,
  `notes` TEXT DEFAULT NULL,
  `creation_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `modification_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  FOREIGN KEY (`collection_id`) REFERENCES `collection`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`binder_id`) REFERENCES `collection_binder`(`id`) ON DELETE CASCADE
);

CREATE INDEX idx_collection_card_collection_id ON collection_card(`collection_id`);
CREATE INDEX idx_collection_card_card_id ON collection_card(`card_id`);
CREATE INDEX idx_collection_card_binder_id ON collection_card(`binder_id`);
CREATE INDEX idx_collection_card_set ON collection_card(`set`);