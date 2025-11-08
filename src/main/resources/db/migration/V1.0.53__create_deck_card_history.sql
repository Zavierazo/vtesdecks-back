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
SELECT 0, dc.`deck_id`, dc.`id` as `card_id`, dc.`number`, NOW()
FROM `deck_card` dc
JOIN `deck` d ON dc.deck_id = d.id
WHERE d.`type` = 'COMMUNITY';

-- Crear triggers para seguimiento automático solo si el deck es COMMUNITY
DELIMITER $$

-- Trigger para INSERT en deck_card
CREATE TRIGGER `deck_card_insert_history`
    AFTER INSERT ON `deck_card`
    FOR EACH ROW
BEGIN
    IF (SELECT `type` FROM `deck` WHERE `id` = NEW.deck_id) = 'COMMUNITY' THEN
        INSERT INTO `deck_card_history` (`deck_id`, `card_id`, `number`, `action`)
        VALUES (NEW.deck_id, NEW.id, NEW.number, 0);
END IF;
END$$

-- Trigger para UPDATE en deck_card
CREATE TRIGGER `deck_card_update_history`
    AFTER UPDATE ON `deck_card`
    FOR EACH ROW
BEGIN
    IF (SELECT `type` FROM `deck` WHERE `id` = NEW.deck_id) = 'COMMUNITY' THEN
        INSERT INTO `deck_card_history` (`deck_id`, `card_id`, `number`, `action`)
        VALUES (NEW.deck_id, NEW.id, NEW.number, 1);
END IF;
END$$

-- Trigger para DELETE en deck_card
CREATE TRIGGER `deck_card_delete_history`
    AFTER DELETE ON `deck_card`
    FOR EACH ROW
BEGIN
    IF (SELECT `type` FROM `deck` WHERE `id` = OLD.deck_id) = 'COMMUNITY' THEN
        INSERT INTO `deck_card_history` (`deck_id`, `card_id`, `number`, `action`)
        VALUES (OLD.deck_id, OLD.id, OLD.number, 2);
END IF;
END$$

DELIMITER ;