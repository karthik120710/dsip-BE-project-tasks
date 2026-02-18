#!/usr/bin/env python3
"""
Daily stock price updater.

Fetches the previous trading day's closing price for all stocks in the DB
and updates the cached price. Intended to run via system cron.

Primary source: Yahoo Finance (yfinance)
Fallback: REST API (GET /api/stocks/close)
"""

import os
import sys
from datetime import datetime, timezone

import psycopg2
import requests
import yfinance as yf


def get_env(name, default=None):
    value = os.environ.get(name, default)
    if value is None:
        print(f"ERROR: Required environment variable {name} is not set")
        sys.exit(1)
    return value


def connect_db():
    return psycopg2.connect(
        host=get_env("DB_HOST", "localhost"),
        port=int(get_env("DB_PORT", "5432")),
        dbname=get_env("DB_NAME", "dsip"),
        user=get_env("DB_USERNAME", "postgres"),
        password=get_env("DB_PASSWORD", "postgres"),
    )


def fetch_all_stocks(conn):
    with conn.cursor() as cur:
        cur.execute("SELECT id, stock_symbol, listed_exchange FROM stocks")
        return cur.fetchall()


def fetch_price_yfinance(symbol):
    """Fetch the most recent closing price via yfinance."""
    ticker = yf.Ticker(symbol)
    hist = ticker.history(period="2d")
    if hist.empty:
        return None
    return float(hist["Close"].iloc[-1])


def fetch_price_rest(symbol, app_host, app_port):
    """Fallback: fetch closing price from the Spring Boot REST API."""
    url = f"http://{app_host}:{app_port}/api/stocks/close"
    resp = requests.get(url, params={"symbol": symbol, "exchange": "US"}, timeout=10)
    resp.raise_for_status()
    data = resp.json()
    price = data.get("closePrice")
    if price is None:
        return None
    return float(price)


def update_stock_price(conn, stock_id, price):
    with conn.cursor() as cur:
        cur.execute(
            "UPDATE stocks SET last_date_market_closing_price = %s, "
            "last_updated_date = NOW() WHERE id = %s",
            (price, stock_id),
        )
    conn.commit()


def main():
    app_host = os.environ.get("APP_HOST", "localhost")
    app_port = os.environ.get("APP_PORT", "8080")

    print(f"[{datetime.now(timezone.utc).isoformat()}] Starting stock price update")

    conn = connect_db()
    try:
        stocks = fetch_all_stocks(conn)
        print(f"Found {len(stocks)} stocks to update")

        updated = 0
        failed = 0

        for stock_id, symbol, exchange in stocks:
            price = None
            source = None

            # Primary: yfinance
            try:
                price = fetch_price_yfinance(symbol)
                if price is not None:
                    source = "yfinance"
            except Exception as e:
                print(f"  [{symbol}] yfinance error: {e}")

            # Fallback: REST API
            if price is None:
                try:
                    price = fetch_price_rest(symbol, app_host, app_port)
                    if price is not None:
                        source = "rest-api"
                except Exception as e:
                    print(f"  [{symbol}] REST API error: {e}")

            if price is not None:
                try:
                    update_stock_price(conn, stock_id, price)
                    print(f"  [{symbol}] updated to {price:.2f} (source: {source})")
                    updated += 1
                except Exception as e:
                    print(f"  [{symbol}] DB update error: {e}")
                    conn.rollback()
                    failed += 1
            else:
                print(f"  [{symbol}] FAILED - could not fetch price")
                failed += 1

        print(f"\nSummary: {updated} updated, {failed} failed, {len(stocks)} total")
    finally:
        conn.close()

    print(f"[{datetime.now(timezone.utc).isoformat()}] Done")


if __name__ == "__main__":
    main()
