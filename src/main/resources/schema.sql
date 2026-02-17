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
    listed_exchange VARCHAR(10) NOT NULL CHECK (
        listed_exchange IN ('US', 'NSE', 'BSE')
    ),
    stock_type SMALLINT NOT NULL DEFAULT 2 CHECK (
        stock_type IN (1, 2, 3, 4)
    ), -- 1=PENNY, 2=MIDCAP, 3=LARGECAP, 4=ETF
    last_date_market_closing_price DOUBLE PRECISION, -- Previous day's closing price (used for lock-in calculations)
    last_updated_date TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT stocks_symbol_unique UNIQUE (stock_symbol)
);

CREATE INDEX IF NOT EXISTS idx_stocks_symbol ON stocks (stock_symbol);

CREATE INDEX IF NOT EXISTS idx_stocks_exchange ON stocks (listed_exchange);

CREATE TABLE IF NOT EXISTS dsip_trackers (
    tracker_id SERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    stock_id BIGINT NOT NULL REFERENCES stocks (id),
    conviction_period_years DOUBLE PRECISION NOT NULL,
    total_capital_planned DOUBLE PRECISION NOT NULL,
    partition_days SMALLINT NOT NULL,
    deployment_style SMALLINT NOT NULL,
    base_conviction_score SMALLINT NOT NULL,
    initial_invested_amount DOUBLE PRECISION NOT NULL,
    initial_shares_held DOUBLE PRECISION NOT NULL,
    status SMALLINT NOT NULL,
    active_partition_index SMALLINT,
    total_capital_invested_so_far DOUBLE PRECISION NOT NULL DEFAULT 0,
    shares_held_so_far DOUBLE PRECISION NOT NULL DEFAULT 0,
    is_fractional_shares_allowed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Covers: WHERE user_id = ? ORDER BY created_at DESC (getAllTrackers)
CREATE INDEX IF NOT EXISTS idx_trackers_user_created ON dsip_trackers (user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS dsip_partitions (
    partition_id SERIAL PRIMARY KEY,
    tracker_id INTEGER NOT NULL REFERENCES dsip_trackers (tracker_id),
    partition_index SMALLINT NOT NULL,
    expected_partition_days SMALLINT NOT NULL,
    partition_capital_allocated DOUBLE PRECISION NOT NULL,
    capital_invested_so_far DOUBLE PRECISION NOT NULL DEFAULT 0,
    no_of_shares_bought DOUBLE PRECISION NOT NULL DEFAULT 0,
    successful_growth_count SMALLINT NOT NULL DEFAULT 0,
    avg_negative_deviation DOUBLE PRECISION DEFAULT 0,
    max_negative_deviation DOUBLE PRECISION DEFAULT 0,
    negative_deviation_count SMALLINT DEFAULT 0,
    partition_end_date TIMESTAMP WITH TIME ZONE,
    status SMALLINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Covers: WHERE tracker_id = ? AND status = 1 (findActivePartitionByTrackerId)
CREATE INDEX IF NOT EXISTS idx_partitions_tracker_status ON dsip_partitions (tracker_id, status);

-- Covers: WHERE tracker_id = ? ORDER BY partition_index (findPartitionsByTrackerId)
CREATE INDEX IF NOT EXISTS idx_partitions_tracker_index ON dsip_partitions (tracker_id, partition_index);

CREATE TABLE IF NOT EXISTS dsip_executions (
    execution_id SERIAL PRIMARY KEY,
    tracker_id INTEGER NOT NULL REFERENCES dsip_trackers (tracker_id),
    partition_id INTEGER NOT NULL REFERENCES dsip_partitions (partition_id),
    lock_in_percentage DOUBLE PRECISION NOT NULL,
    conviction_override SMALLINT,
    executed_amount DOUBLE PRECISION NOT NULL,
    execution_price DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- For foreign key constraint and partition-based lookups
CREATE INDEX IF NOT EXISTS idx_exec_partition ON dsip_executions (partition_id);

-- Covers: WHERE tracker_id = ? ORDER BY created_at DESC LIMIT ? (getRecentExecutions)
CREATE INDEX IF NOT EXISTS idx_exec_tracker_date ON dsip_executions (tracker_id, created_at DESC);