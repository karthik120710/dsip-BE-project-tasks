-- Migration: Convert columns to DOUBLE PRECISION and add deviation tracking columns
-- Date: 2026-02-08

-- 1. dsip_trackers: Convert INTEGER fields to DOUBLE PRECISION
ALTER TABLE dsip_trackers
ALTER COLUMN conviction_period_years TYPE DOUBLE PRECISION,
ALTER COLUMN total_capital_planned TYPE DOUBLE PRECISION,
ALTER COLUMN initial_invested_amount TYPE DOUBLE PRECISION,
ALTER COLUMN initial_shares_held TYPE DOUBLE PRECISION;

-- 2. dsip_partitions: Convert INTEGER fields to DOUBLE PRECISION
ALTER TABLE dsip_partitions
ALTER COLUMN partition_capital_allocated TYPE DOUBLE PRECISION;

-- 3. dsip_partitions: Add new deviation tracking columns
ALTER TABLE dsip_partitions
ADD COLUMN IF NOT EXISTS avg_negative_deviation DOUBLE PRECISION DEFAULT 0,
ADD COLUMN IF NOT EXISTS max_negative_deviation DOUBLE PRECISION DEFAULT 0,
ADD COLUMN IF NOT EXISTS negative_deviation_count SMALLINT DEFAULT 0;

-- 4. dsip_executions: Convert SMALLINT fields to DOUBLE PRECISION
ALTER TABLE dsip_executions
ALTER COLUMN lock_in_percentage TYPE DOUBLE PRECISION;