-- Create stocks table for caching daily closing stock prices
-- This table serves as a cache to avoid repeated external API calls

CREATE TABLE IF NOT EXISTS stocks (
    id BIGSERIAL PRIMARY KEY,
    stock_symbol VARCHAR(50) NOT NULL UNIQUE,
    stock_name VARCHAR(255),
    listed_exchange VARCHAR(10) NOT NULL CHECK (listed_exchange IN ('US', 'NSE', 'BSE')),
    last_date_market_closing_price DOUBLE PRECISION,
    last_updated_date TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT stocks_symbol_unique UNIQUE (stock_symbol)
);

-- Create index on stock_symbol for faster lookups
CREATE INDEX idx_stocks_symbol ON stocks(stock_symbol);

-- Create index on exchange for filtering
CREATE INDEX idx_stocks_exchange ON stocks(listed_exchange);

-- Add comments for documentation
COMMENT ON TABLE stocks IS 'Cache table for daily closing stock prices to avoid repeated external API calls';
COMMENT ON COLUMN stocks.stock_symbol IS 'Unique stock ticker symbol (e.g., AAPL, RELIANCE)';
COMMENT ON COLUMN stocks.stock_name IS 'Company name';
COMMENT ON COLUMN stocks.listed_exchange IS 'Exchange where stock is listed (US, NSE, or BSE)';
COMMENT ON COLUMN stocks.last_date_market_closing_price IS 'Last recorded closing price';
COMMENT ON COLUMN stocks.last_updated_date IS 'Date when closing price was last updated';
