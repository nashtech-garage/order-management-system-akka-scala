-- Payment Service Database Schema
-- Version 1: Create payments table with indexes

-- Create payments table
CREATE TABLE IF NOT EXISTS payments (
  id BIGSERIAL PRIMARY KEY,
  order_id BIGINT NOT NULL,
  created_by BIGINT NOT NULL,
  amount NUMERIC(12, 2) NOT NULL CHECK (amount >= 0),
  payment_method VARCHAR(50) NOT NULL DEFAULT 'auto',
  status VARCHAR(20) NOT NULL DEFAULT 'success',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT payments_status_check CHECK (status IN ('success', 'failed'))
);

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_payments_order_id ON payments(order_id);
CREATE INDEX IF NOT EXISTS idx_payments_created_by ON payments(created_by);
CREATE INDEX IF NOT EXISTS idx_payments_status ON payments(status);
CREATE INDEX IF NOT EXISTS idx_payments_created_at ON payments(created_at DESC);

-- Comments for documentation
COMMENT ON TABLE payments IS 'Stores payment transaction records for orders';
COMMENT ON COLUMN payments.id IS 'Primary key for payment record';
COMMENT ON COLUMN payments.order_id IS 'Reference to the order being paid';
COMMENT ON COLUMN payments.created_by IS 'User ID who created the payment';
COMMENT ON COLUMN payments.amount IS 'Payment amount in decimal format';
COMMENT ON COLUMN payments.payment_method IS 'Payment method used (default: auto)';
COMMENT ON COLUMN payments.status IS 'Current payment status (success or failed only)';
COMMENT ON COLUMN payments.created_at IS 'Timestamp when payment was created';
