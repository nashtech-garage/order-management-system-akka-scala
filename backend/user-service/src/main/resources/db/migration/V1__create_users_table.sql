-- Flyway Migration V1: Create users table with comprehensive account management features
-- This migration creates the main users table with all necessary fields for user management

DROP TABLE IF EXISTS users CASCADE;

-- Create users table
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'user',
    status VARCHAR(50) NOT NULL DEFAULT 'active',
    phone_number VARCHAR(20),
    last_login TIMESTAMP,
    login_attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Add constraints inline
    CONSTRAINT chk_user_status CHECK (status IN ('active', 'locked')),
    CONSTRAINT chk_user_role CHECK (role IN ('user', 'admin')),
    CONSTRAINT chk_login_attempts CHECK (login_attempts >= 0)
);

-- Create indexes for better query performance
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_users_status ON users(status);
CREATE INDEX idx_users_created_at ON users(created_at);
CREATE INDEX idx_users_last_login ON users(last_login);

-- Create composite indexes for common queries
CREATE INDEX idx_users_role_status ON users(role, status);
CREATE INDEX idx_users_status_created_at ON users(status, created_at);
