-- Migration script to convert from BIGINT IDs to UUID
-- WARNING: This will drop and recreate the tables. Make sure to backup your data first!

-- Enable UUID extension if not already enabled (PostgreSQL 13+ has gen_random_uuid() built-in)
-- For older versions, uncomment the following line:
-- CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Drop existing tables (WARNING: This will delete all data)
DROP TABLE IF EXISTS whitelisted_emails CASCADE;
DROP TABLE IF EXISTS users CASCADE;

-- Create users table with UUID
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255),
    profile_picture VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Create index on email for faster lookups
CREATE INDEX idx_users_email ON users(email);

-- Create whitelisted_emails table with UUID
CREATE TABLE whitelisted_emails (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    added_by VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Create index on email for faster lookups
CREATE INDEX idx_whitelisted_emails_email ON whitelisted_emails(email);

-- Optional: If you want to preserve existing data, use this approach instead:
-- 1. Create new tables with _new suffix
-- 2. Copy data with UUID generation
-- 3. Drop old tables
-- 4. Rename new tables

/*
-- Alternative migration preserving data:

-- Create new users table
CREATE TABLE users_new (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255),
    profile_picture VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Copy existing data
INSERT INTO users_new (email, name, profile_picture, created_at)
SELECT email, name, profile_picture, created_at FROM users;

-- Create new whitelisted_emails table
CREATE TABLE whitelisted_emails_new (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    added_by VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Copy existing data
INSERT INTO whitelisted_emails_new (email, added_by, created_at)
SELECT email, added_by, created_at FROM whitelisted_emails;

-- Drop old tables
DROP TABLE whitelisted_emails CASCADE;
DROP TABLE users CASCADE;

-- Rename new tables
ALTER TABLE users_new RENAME TO users;
ALTER TABLE whitelisted_emails_new RENAME TO whitelisted_emails;

-- Create indexes
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_whitelisted_emails_email ON whitelisted_emails(email);
*/
