-- Change reference_id from int to varchar to support storing deck IDs and other string references
ALTER TABLE `user_notification` MODIFY COLUMN `reference_id` varchar(250) NULL;

