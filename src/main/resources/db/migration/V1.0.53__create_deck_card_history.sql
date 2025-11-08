-- Create deck_card_history table
CREATE TABLE `deck_card_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `action` tinyint NOT NULL,
  `deck_id` varchar(250) NOT NULL,
  `card_id` int NOT NULL,
  `number` int NOT NULL,
  `tag` int NULL,
  `creation_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_deck_card_history_deck_id` (`deck_id`),
  INDEX `idx_deck_card_history_creation_date` (`creation_date`),
  INDEX `idx_deck_card_history_tag` (`tag`),
  FOREIGN KEY (`deck_id`) REFERENCES `deck`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Initialize history with current deck_card content
INSERT INTO `deck_card_history` (`action`, `deck_id`, `card_id`, `number`, `creation_date`)
SELECT 0, `deck_id`, `id` as `card_id`, `number`, NOW()
FROM `deck_card`;

-- Create triggers for automatic history tracking
DELIMITER $$

-- Trigger for INSERT operations
CREATE TRIGGER `deck_card_insert_history`
    AFTER INSERT ON `deck_card`
    FOR EACH ROW
BEGIN
    INSERT INTO `deck_card_history` (`deck_id`, `card_id`, `number`, `action`)
    VALUES (NEW.deck_id, NEW.id, NEW.number, 0);
END$$

-- Trigger for UPDATE operations
CREATE TRIGGER `deck_card_update_history`
    AFTER UPDATE ON `deck_card`
    FOR EACH ROW
BEGIN
    INSERT INTO `deck_card_history` (`deck_id`, `card_id`, `number`, `action`)
    VALUES (NEW.deck_id, NEW.id, NEW.number, 1);
END$$

-- Trigger for DELETE operations
CREATE TRIGGER `deck_card_delete_history`
    AFTER DELETE ON `deck_card`
    FOR EACH ROW
BEGIN
    INSERT INTO `deck_card_history` (`deck_id`, `card_id`, `number`, `action`)
    VALUES (OLD.deck_id, OLD.id, OLD.number, 2);
END$$

DELIMITER ;
