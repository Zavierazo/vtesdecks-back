-- Create collection_card_history table
CREATE TABLE `collection_card_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `action` tinyint NOT NULL,
  `collection_id` int NOT NULL,
  `card_id` int NOT NULL,
  `number` int NOT NULL,
  `set` varchar(255) NULL,
  `condition` varchar(2) NULL,
  `language` varchar(2) NULL,
  `binder_id` int NULL,
  `creation_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_cch_collection_id` (`collection_id`),
  INDEX `idx_cch_collection_card` (`collection_id`, `card_id`),
  INDEX `idx_cch_collection_binder` (`collection_id`, `binder_id`),
  INDEX `idx_cch_creation_date` (`creation_date`),
  FOREIGN KEY (`collection_id`) REFERENCES `collection`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Backfill existing collection content as action 0 (INSERT),
-- dating each entry from the card's last update (fallback to its creation date)
INSERT INTO `collection_card_history`
  (`action`,`collection_id`,`card_id`,`number`,`set`,`condition`,`language`,`binder_id`,`creation_date`)
SELECT 0, cc.`collection_id`, cc.`card_id`, cc.`number`, cc.`set`, cc.`condition`, cc.`language`, cc.`binder_id`,
       COALESCE(cc.`modification_date`, cc.`creation_date`)
FROM `collection_card` cc;

-- Triggers for automatic change tracking on collection_card
DELIMITER $$

-- Trigger for INSERT on collection_card
CREATE TRIGGER `collection_card_insert_history`
    AFTER INSERT ON `collection_card`
    FOR EACH ROW
BEGIN
    INSERT INTO `collection_card_history`
        (`action`,`collection_id`,`card_id`,`number`,`set`,`condition`,`language`,`binder_id`)
    VALUES (0, NEW.collection_id, NEW.card_id, NEW.number, NEW.`set`, NEW.`condition`, NEW.language, NEW.binder_id);
END$$

-- Trigger for UPDATE on collection_card
CREATE TRIGGER `collection_card_update_history`
    AFTER UPDATE ON `collection_card`
    FOR EACH ROW
BEGIN
    INSERT INTO `collection_card_history`
        (`action`,`collection_id`,`card_id`,`number`,`set`,`condition`,`language`,`binder_id`)
    VALUES (1, NEW.collection_id, NEW.card_id, NEW.number, NEW.`set`, NEW.`condition`, NEW.language, NEW.binder_id);
END$$

-- Trigger for DELETE on collection_card
CREATE TRIGGER `collection_card_delete_history`
    AFTER DELETE ON `collection_card`
    FOR EACH ROW
BEGIN
    INSERT INTO `collection_card_history`
        (`action`,`collection_id`,`card_id`,`number`,`set`,`condition`,`language`,`binder_id`)
    VALUES (2, OLD.collection_id, OLD.card_id, OLD.number, OLD.`set`, OLD.`condition`, OLD.language, OLD.binder_id);
END$$

DELIMITER ;
