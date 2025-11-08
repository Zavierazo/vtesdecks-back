-- Alter deck_id fields to varchar(100)

-- First, we need to disable foreign key checks to avoid constraint issues
SET FOREIGN_KEY_CHECKS = 0;

-- Alter the main deck table id field (which serves as deck_id for other tables)
ALTER TABLE `deck` MODIFY COLUMN `id` varchar(100) NOT NULL;

-- Alter deck_id in all related tables
ALTER TABLE `deck_card` MODIFY COLUMN `deck_id` varchar(100) NOT NULL;
ALTER TABLE `deck_view` MODIFY COLUMN `deck_id` varchar(100) NOT NULL;
ALTER TABLE `deck_user` MODIFY COLUMN `deck_id` varchar(100) NOT NULL;
ALTER TABLE `deck_card_history` MODIFY COLUMN `deck_id` varchar(100) NOT NULL;

-- Re-enable foreign key checks
SET FOREIGN_KEY_CHECKS = 1;
