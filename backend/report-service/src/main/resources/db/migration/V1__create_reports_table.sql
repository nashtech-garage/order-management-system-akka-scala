-- Create scheduled_reports table
CREATE TABLE IF NOT EXISTS scheduled_reports (
    id BIGSERIAL PRIMARY KEY,
    report_type VARCHAR(50) NOT NULL,
    report_date DATE NOT NULL,
    total_orders INTEGER NOT NULL DEFAULT 0,
    total_revenue DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    average_order_value DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    orders_by_status JSONB NOT NULL DEFAULT '{}'::jsonb,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(report_type, report_date)
);

-- Create indexes for efficient querying
CREATE INDEX idx_scheduled_reports_date ON scheduled_reports(report_date DESC);
CREATE INDEX idx_scheduled_reports_type ON scheduled_reports(report_type);
CREATE INDEX idx_scheduled_reports_generated_at ON scheduled_reports(generated_at DESC);

-- Create index on JSONB columns for faster queries
CREATE INDEX idx_scheduled_reports_orders_by_status ON scheduled_reports USING GIN (orders_by_status);
