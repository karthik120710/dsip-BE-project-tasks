-- Add stock_type column to stocks table
-- 1=PENNY (24% target), 2=MIDCAP (21% target), 3=LARGECAP (15% target), 4=ETF (15% target)
ALTER TABLE stocks ADD COLUMN IF NOT EXISTS stock_type SMALLINT NOT NULL DEFAULT 2;

-- Add constraint for valid stock_type values
ALTER TABLE stocks ADD CONSTRAINT chk_stock_type CHECK (stock_type IN (1, 2, 3, 4));

-- Add index on stock_type for filtering
CREATE INDEX IF NOT EXISTS idx_stocks_type ON stocks (stock_type);
