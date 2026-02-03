-- Quick script to create oms_reports database and table
-- Run this in your PostgreSQL container or client

-- Create the database
CREATE DATABASE oms_reports;

-- Connect to the database
\c oms_reports;

-- Create the scheduled_reports table
CREATE TABLE IF NOT EXISTS scheduled_reports (
    id BIGSERIAL PRIMARY KEY,
    report_type VARCHAR(50) NOT NULL,
    report_date DATE NOT NULL,
    total_orders INTEGER NOT NULL DEFAULT 0,
    total_revenue DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    average_order_value DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    orders_by_status TEXT NOT NULL DEFAULT '{}',
    metadata TEXT NOT NULL DEFAULT '{}',
    generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(report_type, report_date)
);

-- Create indexes for performance
CREATE INDEX idx_scheduled_reports_date ON scheduled_reports(report_date DESC);
CREATE INDEX idx_scheduled_reports_type ON scheduled_reports(report_type);
CREATE INDEX idx_scheduled_reports_generated_at ON scheduled_reports(generated_at DESC);

-- Verify the table was created
\dt scheduled_reports;
