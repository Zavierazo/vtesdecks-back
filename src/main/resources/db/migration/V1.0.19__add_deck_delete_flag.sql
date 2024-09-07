ALTER TABLE `deck` ADD COLUMN `deleted` boolean NOT NULL DEFAULT false AFTER `verified`;
