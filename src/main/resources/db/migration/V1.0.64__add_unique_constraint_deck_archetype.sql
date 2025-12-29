ALTER TABLE `deck_archetype`
    ADD CONSTRAINT `uq_deck_archetype_name` UNIQUE (`name`);
ALTER TABLE `deck_archetype`
    ADD CONSTRAINT `uq_deck_archetype_deck` UNIQUE (`deck_id`);

