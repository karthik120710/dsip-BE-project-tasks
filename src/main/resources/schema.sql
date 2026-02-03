-- Spring Session tables
CREATE TABLE IF NOT EXISTS SPRING_SESSION (
    PRIMARY_ID CHAR(36) NOT NULL,
    SESSION_ID CHAR(36) NOT NULL,
    CREATION_TIME BIGINT NOT NULL,
    LAST_ACCESS_TIME BIGINT NOT NULL,
    MAX_INACTIVE_INTERVAL INT NOT NULL,
    EXPIRY_TIME BIGINT NOT NULL,
    PRINCIPAL_NAME VARCHAR(100),
    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);

CREATE UNIQUE INDEX IF NOT EXISTS SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);

CREATE INDEX IF NOT EXISTS SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);

CREATE INDEX IF NOT EXISTS SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE IF NOT EXISTS SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36) NOT NULL,
    ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES BYTEA NOT NULL,
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (
        SESSION_PRIMARY_ID,
        ATTRIBUTE_NAME
    ),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES SPRING_SESSION (PRIMARY_ID) ON DELETE CASCADE
);

-- Application tables
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid (),
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255),
    profile_picture TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users (email);

CREATE TABLE IF NOT EXISTS whitelisted_emails (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid (),
    email VARCHAR(255) NOT NULL UNIQUE,
    added_by VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_whitelisted_emails_email ON whitelisted_emails (email);

-- Stocks table for caching daily closing stock prices
CREATE TABLE IF NOT EXISTS stocks (
    id BIGSERIAL PRIMARY KEY,
    stock_symbol VARCHAR(50) NOT NULL UNIQUE,
    stock_name VARCHAR(255),
    listed_exchange VARCHAR(10) NOT NULL CHECK (listed_exchange IN ('US', 'NSE', 'BSE')),
    last_date_market_closing_price DOUBLE PRECISION,
    last_updated_date TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT stocks_symbol_unique UNIQUE (stock_symbol)
);

CREATE INDEX IF NOT EXISTS idx_stocks_symbol ON stocks(stock_symbol);
CREATE INDEX IF NOT EXISTS idx_stocks_exchange ON stocks(listed_exchange);

-- Stocks table for caching daily closing stock prices
CREATE TABLE IF NOT EXISTS stocks (
    id BIGSERIAL PRIMARY KEY,
    stock_symbol VARCHAR(50) NOT NULL UNIQUE,
    stock_name VARCHAR(255),
    listed_exchange VARCHAR(10) NOT NULL CHECK (listed_exchange IN ('US', 'NSE', 'BSE')),
    last_date_market_closing_price DOUBLE PRECISION,
    last_updated_date TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT stocks_symbol_unique UNIQUE (stock_symbol)
);

CREATE INDEX IF NOT EXISTS idx_stocks_symbol ON stocks(stock_symbol);
CREATE INDEX IF NOT EXISTS idx_stocks_exchange ON stocks(listed_exchange);

CREATE TABLE IF NOT EXISTS dsip_trackers (
    tracker_id SERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    stock_symbol VARCHAR(20) NOT NULL,
    conviction_period_years SMALLINT NOT NULL,
    total_capital_planned INTEGER NOT NULL,
    partition_days SMALLINT NOT NULL,
    deployment_style SMALLINT NOT NULL,
    base_conviction_score SMALLINT NOT NULL,
    initial_invested_amount INTEGER NOT NULL,
    initial_shares_held INTEGER NOT NULL,
    status SMALLINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    current_partition_index SMALLINT,
    CONSTRAINT uq_tracker_user_stock UNIQUE (user_id, stock_symbol)
);

-- Covers: WHERE user_id = ? ORDER BY created_at DESC (getAllTrackers)
CREATE INDEX IF NOT EXISTS idx_trackers_user_created ON dsip_trackers (user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS dsip_partitions (
    partition_id SERIAL PRIMARY KEY,
    tracker_id INTEGER NOT NULL REFERENCES dsip_trackers (tracker_id),
    partition_index SMALLINT NOT NULL,
    partition_start_date DATE NOT NULL,
    partition_days SMALLINT NOT NULL,
    partition_capital_allocated INTEGER NOT NULL,
    successful_executions_completed SMALLINT NOT NULL,
    capital_deployed_so_far INTEGER NOT NULL,
    active_conviction_score SMALLINT NOT NULL,
    total_lockin_percentage_count DECIMAL(10, 2),
    consistent_growth_count SMALLINT,
    status SMALLINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    partition_end_date DATE
);

-- Covers: WHERE tracker_id = ? AND status = 1 (findActivePartitionByTrackerId)
CREATE INDEX IF NOT EXISTS idx_partitions_tracker_status ON dsip_partitions (tracker_id, status);

-- Covers: WHERE tracker_id = ? ORDER BY partition_index (findPartitionsByTrackerId)
CREATE INDEX IF NOT EXISTS idx_partitions_tracker_index ON dsip_partitions (tracker_id, partition_index);

CREATE TABLE IF NOT EXISTS dsip_executions (
    execution_id SERIAL PRIMARY KEY,
    tracker_id INTEGER NOT NULL REFERENCES dsip_trackers (tracker_id),
    partition_id INTEGER NOT NULL REFERENCES dsip_partitions (partition_id),
    execution_date DATE NOT NULL,
    lock_in_percentage SMALLINT NOT NULL,
    conviction_override SMALLINT,
    executed_amount SMALLINT NOT NULL,
    execution_price DECIMAL NOT NULL,
    last_executed_avg_price DECIMAL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- For foreign key constraint and partition-based lookups
CREATE INDEX IF NOT EXISTS idx_exec_partition ON dsip_executions (partition_id);

-- Covers: WHERE tracker_id = ? ORDER BY execution_date DESC LIMIT ? (getRecentExecutions)
CREATE INDEX IF NOT EXISTS idx_exec_tracker_date ON dsip_executions (
    tracker_id,
    execution_date DESC
);