# DSIP Backend API Documentation

## Base URL
```
http://localhost:8080/api
```

## Authentication
All endpoints require OAuth2 authentication (session cookie or bearer token).
Admin endpoints require `X-Admin-API-Key` header.

---

## Tracker Management

### Create Tracker
```http
POST /dsip-trackers
```

**Request:**
```json
{
  "stock_symbol": "AAPL",
  "conviction_period_years": 3,
  "total_capital_planned": 100000,
  "partition_months": 2,
  "deployment_style": "MODERATE",
  "base_conviction_score": 80,
  "is_fractional_shares_allowed": true,
  "initial_invested_amount": 5000,
  "initial_shares_held": 25
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `stock_symbol` | string | Yes | Stock ticker (e.g., "AAPL") |
| `conviction_period_years` | number | Yes | Investment horizon in years |
| `total_capital_planned` | number | Yes | Total capital to invest |
| `partition_months` | integer | Yes | Duration per partition in months |
| `deployment_style` | string | Yes | `AGGRESSIVE`, `MODERATE`, `GRADUAL` |
| `base_conviction_score` | integer | Yes | Score 0-100 |
| `is_fractional_shares_allowed` | boolean | No | Default: false |
| `initial_invested_amount` | number | No | Pre-existing investment |
| `initial_shares_held` | number | No | Pre-existing shares |

---

### Get Portfolio (All Trackers)
```http
GET /dsip-trackers
```

**Response:**
```json
{
  "total_market_value": 125000.50,
  "total_capital_invested_so_far": 100000,
  "net_profit_percentage": 25.0,
  "dsip_total_market_value": 120000.50,
  "dsip_total_capital_invested_so_far": 95000,
  "dsip_total_net_profit_percentage": 26.3,
  "dsip_trackers": [...]
}
```

---

### Get Tracker Details
```http
GET /dsip-trackers/{trackerId}
```

**Response:**
```json
{
  "id": 1,
  "symbol": "AAPL",
  "name": "Apple Inc.",
  "status": "ACTIVE",
  "conviction_period_years": 3,
  "total_capital_planned": 100000,
  "partition_months": 2,
  "deployment_style": "MODERATE",
  "base_conviction_score": 80,
  "total_capital_invested_so_far": 50000,
  "current_total_value": 57500,
  "net_profit_percentage": 15.0,
  "dsip_total_market_value": 52500,
  "dsip_total_capital_invested_so_far": 45000,
  "dsip_net_profit_percentage": 16.67,
  "active_partition_index": 2,
  "total_cycles": 3,
  "history": [...],
  "live_investment_cycle": {
    "total_capital_invested_so_far": 8000,
    "partition_progress": 0.45,
    "net_profit_percentage": 12.5
  }
}
```

---

### Update Tracker
```http
PUT /dsip-trackers/{trackerId}
```

---

### Delete Tracker
```http
DELETE /dsip-trackers/{trackerId}
```

---

### Get Partition Details
```http
GET /dsip-trackers/{trackerId}/partitions/{partitionIndex}
```

---

### Get Executions
```http
GET /dsip-trackers/{trackerId}/executions?limit=6
```

---

### End Partition Action
```http
POST /dsip-trackers/end-action?trackerId={id}&partitionIndex={index}
```

---

## Execution API

### Execute Trade
```http
POST /dsip-trackers/{trackerId}/execute
```

**Request:**
```json
{
  "lock_in_percentage": -3.5,
  "conviction_override": 85,
  "executed_amount": 1500,
  "execution_price": 182.50
}
```

| Field | Type | Description |
|-------|------|-------------|
| `lock_in_percentage` | number | Lock-in % relative to prev close |
| `conviction_override` | integer | Current conviction (0-100) |
| `executed_amount` | number | Amount invested |
| `execution_price` | number | Execution price |

**Response:**
```json
{
  "status": "EXECUTED",
  "end_reason": null
}
```

**End Reasons:** `SUCCESS`, `KILL_SWITCH`, `NEUTRAL_PARTITION`, or `null` (active)

---

## Recommendation API

### Get Investment Recommendation
```http
GET /dsip-trackers/{trackerId}/recommendation?lock_in_pct=-5.0
```

**Response:**
```json
{
  "tracker_id": 1,
  "recommended_amount": 2500.00,
  "breakdown": {
    "neutral_capital": 1500.00,
    "opportunity_multiplier": 1.45,
    "contingency_multiplier": 1.15,
    "final_multiplier": 1.67
  },
  "signals": {
    "avg_holding_price": 185.50,
    "avg_deviation_pct": -2.5,
    "avg_signal": 0.35,
    "lock_in_pct": -5.0,
    "lock_in_signal": 0.65,
    "raw_opportunity_signal": 0.50,
    "conviction_amplifier": 1.2,
    "is_abnormal_dip": false
  },
  "partition_status": {
    "partition_index": 2,
    "partition_progress_pct": 45.0,
    "return_progress_pct": 30.0,
    "growth_persistence_pct": 25.0,
    "time_progress_pct": 55.0,
    "capital_progress_pct": 40.0,
    "capital_deployed": 8000.00,
    "capital_remaining": 12000.00,
    "cumulative_return_pct": 8.5
  }
}
```

---

## Sync API

Reconciles DSIP tracker state with actual broker holdings.

### Sync Broker State
```http
POST /dsip/sync
```

**Request:**
```json
{
  "tracker_id": 1,
  "current_total_shares": 150.5,
  "current_total_invested_amount": 28000,
  "reason": "Manual adjustment after dividend reinvestment"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `tracker_id` | integer | Yes | Tracker to sync |
| `current_total_shares` | number | Yes | Actual broker shares |
| `current_total_invested_amount` | number | Yes | Actual invested amount |
| `reason` | string | No | Audit reason |

**Success Response:**
```json
{
  "success": true,
  "message": "Sync completed successfully",
  "dsip_shares": 125.5,
  "dsip_capital_deployed": 23000,
  "total_shares": 150.5,
  "total_invested_amount": 28000
}
```

**Error Response:**
```json
{
  "success": false,
  "message": "Broker shares (100) cannot be less than DSIP shares (125.5)"
}
```

---

## Simulation API

> Available only in `dev`, `test`, `local` profiles.

### Run Simulation
```http
POST /simulation/run
```

**Request:**
```json
{
  "totalCapital": 100000,
  "convictionYears": 3,
  "stockType": "MIDCAP",
  "convictionScore": 80,
  "deploymentStyle": "MODERATE",
  "initialPrice": 100,
  "maxPartitions": 10,
  "expectedDays": 60,
  "scenario": "BULL",
  "randomSeed": 42
}
```

**Scenarios:** `RANDOM`, `BULL`, `BEAR`, `VOLATILE`, `SIDEWAYS`, `CUSTOM`, `HISTORICAL`

**Stock Types:** `PENNY`, `MIDCAP`, `LARGECAP`, `ETF`

---

### Run Historical Simulation
```http
POST /simulation/run-historical
Content-Type: multipart/form-data
```

**Form Data:**
- `file`: CSV with OHLC data
- `config`: JSON configuration

**CSV Format:**
```csv
date,open,high,low,close,volume
2024-01-02,185.50,187.00,184.25,186.75,50000000
```

**Additional Options:**
- `executionPriceType`: `OPEN`, `CLOSE`, `VWAP`, `LOW`, `HIGH`
- `lockInReferenceType`: `PREV_CLOSE`, `OPEN`

---

### Run from Server File
```http
POST /simulation/run-from-file
```

```json
{
  "csvFilePath": "/data/historical/AAPL_2023.csv",
  "totalCapital": 100000
}
```

---

### Compare Scenarios
```http
POST /simulation/compare
```

Runs all scenarios and returns comparison.

---

### Get Report
```http
POST /simulation/report
```

Returns text-based simulation report.

---

## Admin API

> Requires `X-Admin-API-Key` header.

### Whitelist Endpoints
```http
GET    /admin/whitelist                 # List all
POST   /admin/whitelist                 # Add email {"email": "..."}
POST   /admin/whitelist/bulk            # Bulk add ["email1", "email2"]
DELETE /admin/whitelist/{email}         # Remove
GET    /admin/whitelist/check/{email}   # Check status
```

---

## Error Responses

```json
{
  "error": "TRACKER_NOT_FOUND",
  "message": "Tracker with ID 1 not found",
  "timestamp": "2024-01-15T10:30:00Z",
  "path": "/api/dsip-trackers/1"
}
```

| Error Code | HTTP | Description |
|------------|------|-------------|
| `TRACKER_NOT_FOUND` | 404 | Tracker doesn't exist |
| `PARTITION_NOT_FOUND` | 404 | Partition doesn't exist |
| `UNAUTHORIZED_ACCESS` | 403 | User doesn't own tracker |
| `DUPLICATE_TRACKER` | 409 | Tracker exists for stock |
| `INVALID_EXECUTION` | 400 | Invalid execution params |
| `STOCK_NOT_FOUND` | 404 | Stock symbol not found |
| `SYNC_ERROR` | 400 | Sync validation failed |

---

## Health Check
```http
GET /health
```
