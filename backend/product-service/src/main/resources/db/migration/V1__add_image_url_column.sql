-- Flyway Migration V1: Add image_url column to products table
-- Date: 2025-12-30
-- Description: Adds image_url column to store product images (URLs or file paths)

ALTER TABLE products
ADD COLUMN IF NOT EXISTS image_url VARCHAR(500);

-- Add comment to column for documentation
COMMENT ON COLUMN products.image_url IS 'URL or path to product image';
