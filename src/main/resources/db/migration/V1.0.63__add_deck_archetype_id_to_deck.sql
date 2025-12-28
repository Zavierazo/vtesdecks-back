ALTER TABLE `deck`
  ADD COLUMN `deck_archetype_id` INT DEFAULT NULL AFTER `set`,
  ADD INDEX `idx_deck_deck_archetype_id` (`deck_archetype_id`),
  ADD CONSTRAINT `fk_deck_deck_archetype` FOREIGN KEY (`deck_archetype_id`) REFERENCES `deck_archetype` (`id`) ON DELETE SET NULL ON UPDATE CASCADE;

