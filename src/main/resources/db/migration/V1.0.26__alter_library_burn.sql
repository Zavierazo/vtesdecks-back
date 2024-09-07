UPDATE `library` SET `burn`=null;
ALTER TABLE `library` MODIFY COLUMN `burn` boolean NULL;
