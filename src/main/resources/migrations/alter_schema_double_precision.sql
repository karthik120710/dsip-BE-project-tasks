-- Migration: Convert columns to DOUBLE PRECISION and add deviation tracking columns
-- Date: 2026-02-08

-- 1. Convert conviction_period_years to DOUBLE PRECISION
ALTER TABLE dsip_trackers
ALTER COLUMN conviction_period_years TYPE DOUBLE PRECISION;

-- 2. Convert lock_in_percentage to DOUBLE PRECISION
ALTER TABLE dsip_executions
ALTER COLUMN lock_in_percentage TYPE DOUBLE PRECISION;

-- 3. Add new deviation tracking columns to dsip_partitions
ALTER TABLE dsip_partitions
ADD COLUMN IF NOT EXISTS avg_negative_deviation DOUBLE PRECISION,
ADD COLUMN IF NOT EXISTS max_negative_deviation DOUBLE PRECISION,
ADD COLUMN IF NOT EXISTS negative_deviation_count SMALLINT;