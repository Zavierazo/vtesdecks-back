ALTER TABLE `deck_archetype`
    ADD COLUMN `secondary_deck_id` VARCHAR(250) DEFAULT NULL AFTER `deck_id`,
    ADD FOREIGN KEY (`secondary_deck_id`) REFERENCES `deck`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;
ALTER TABLE `deck_archetype`
    ADD CONSTRAINT `uq_deck_archetype_secondary_deck` UNIQUE (`secondary_deck_id`);
