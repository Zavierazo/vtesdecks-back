-- Add locale column to card_shop and add a new unique constraint including locale
ALTER TABLE card_shop
  ADD COLUMN `locale` varchar(10) NULL AFTER `set`;

-- Remove the old unique constraint (will fail if duplicate rows exist)
ALTER TABLE vtesdecks_v1.card_shop DROP KEY card_id;

-- Create a new unique constraint including locale (will fail if duplicate rows exist)
ALTER TABLE card_shop
  ADD UNIQUE KEY card_shop_card_unique (card_id, platform, `set`, `locale`);
