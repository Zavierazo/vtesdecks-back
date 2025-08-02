ALTER TABLE `deck` ADD COLUMN `collection` boolean NOT NULL DEFAULT false AFTER `published`;
ALTER TABLE `deck` ADD COLUMN `set` varchar(100) NULL AFTER `description`;
