ALTER TABLE card_shop MODIFY COLUMN price decimal(10,2) NULL;
ALTER TABLE card_shop ADD COLUMN currency varchar(10) NULL AFTER `price`;