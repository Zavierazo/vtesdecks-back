-- Add stock tracking and dynamic data fields to card_shop table
ALTER TABLE card_shop ADD COLUMN in_stock BOOLEAN NOT NULL DEFAULT TRUE AFTER `currency`;
ALTER TABLE card_shop ADD COLUMN stock_quantity INT NULL AFTER `in_stock`;
ALTER TABLE card_shop ADD COLUMN data JSON NULL AFTER `stock_quantity`;

