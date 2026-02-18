#!/usr/bin/env bash

set -e

# ---- POSTGRES CONFIG ----
PG_HOST="${DB_HOST:?DB_HOST is required}"
PG_DATABASE="${DB_NAME:?DB_NAME is required}"
PG_USERNAME="${DB_USERNAME:?DB_USERNAME is required}"
PG_PASSWORD="${DB_PASSWORD:?DB_PASSWORD is required}"

# ---- CONFIG ----
SERVICE_ACCOUNT_KEY="${SERVICE_ACCOUNT_KEY:?SERVICE_ACCOUNT_KEY is required}"
SHEET_ID="${SHEET_ID:?SHEET_ID is required}"
RANGE="${RANGE:-Sheet1!A1:A1000}"

# ---- Extract fields from JSON ----
CLIENT_EMAIL=$(jq -r .client_email "$SERVICE_ACCOUNT_KEY")
PRIVATE_KEY=$(jq -r .private_key "$SERVICE_ACCOUNT_KEY" | sed 's/\\n/\n/g')
TOKEN_URI=$(jq -r .token_uri "$SERVICE_ACCOUNT_KEY")

# ---- Create JWT Header ----
HEADER=$(printf '{"alg":"RS256","typ":"JWT"}' | openssl base64 -e -A | tr '+/' '-_' | tr -d '=')

# ---- Create JWT Claim ----
NOW=$(date +%s)
EXP=$((NOW + 3600))

CLAIM=$(printf '{
  "iss":"%s",
  "scope":"https://www.googleapis.com/auth/spreadsheets.readonly",
  "aud":"%s",
  "exp":%d,
  "iat":%d
}' "$CLIENT_EMAIL" "$TOKEN_URI" "$EXP" "$NOW" | openssl base64 -e -A | tr '+/' '-_' | tr -d '=')

# ---- Sign JWT ----
SIGNATURE=$(printf "%s.%s" "$HEADER" "$CLAIM" |
  openssl dgst -sha256 -sign <(printf "%s" "$PRIVATE_KEY") |
  openssl base64 -e -A | tr '+/' '-_' | tr -d '=')

JWT="$HEADER.$CLAIM.$SIGNATURE"

# ---- Exchange JWT for Access Token ----
ACCESS_TOKEN=$(curl -s -X POST "$TOKEN_URI" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer" \
  -d "assertion=$JWT" |
  jq -r .access_token)

# ---- Call Google Sheets API ----
(
  echo "BEGIN;"

  echo "DROP TABLE IF EXISTS tmp_whitelisted;"

  echo "CREATE TEMP TABLE tmp_whitelisted (email text, added_by text);"

  echo "COPY tmp_whitelisted(email, added_by) FROM STDIN;"

  curl -s \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    "https://sheets.googleapis.com/v4/spreadsheets/$SHEET_ID/values/$RANGE" |
    jq -e -r '.values[]?[0] // empty | select(length>0) + "\tadmin"'

  echo "\."

  echo "INSERT INTO whitelisted_emails(email, added_by)
        SELECT DISTINCT email, added_by FROM tmp_whitelisted
        ON CONFLICT (email) DO NOTHING;"

  echo "DELETE FROM whitelisted_emails w
        WHERE NOT EXISTS (
          SELECT 1 FROM tmp_whitelisted t
          WHERE t.email = w.email
        );"

  echo "COMMIT;"
) | PGPASSWORD="$PG_PASSWORD" psql \
  -v ON_ERROR_STOP=1 \
  -U "$PG_USERNAME" \
  -d "$PG_DATABASE" \
  -h "$PG_HOST"
