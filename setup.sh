#!/usr/bin/env bash
set -euo pipefail

echo "============================================================"
echo " DSIP Backend - Local Setup Script (Linux/macOS)"
echo "============================================================"
echo ""

# -------------------- Load .env --------------------
if [ ! -f .env ]; then
    echo "[ERROR] .env file not found. Copy .env.example and fill in your values:"
    echo "        cp .env.example .env"
    exit 1
fi

echo "[ENV] Loading .env file..."
set -a
source .env
set +a
echo "[OK] Environment loaded from .env"
echo ""

# -------------------- Java 21 --------------------
if java -version 2>&1 | grep -q '"21'; then
    echo "[OK] Java 21 already installed"
else
    echo "[INSTALLING] Java 21 (Eclipse Temurin)..."
    sudo apt-get update -qq
    sudo apt-get install -y -qq wget apt-transport-https gpg

    wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | \
        gpg --dearmor | sudo tee /usr/share/keyrings/adoptium.gpg > /dev/null
    echo "deb [signed-by=/usr/share/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb $(lsb_release -cs) main" | \
        sudo tee /etc/apt/sources.list.d/adoptium.list

    sudo apt-get update -qq
    sudo apt-get install -y -qq temurin-21-jdk
    echo "[OK] Java 21 installed"
fi
java -version
echo ""

# -------------------- Maven --------------------
if mvn -version 2>&1 | grep -q 'Apache Maven'; then
    echo "[OK] Maven already installed"
else
    echo "[INSTALLING] Maven..."
    sudo apt-get install -y -qq maven
    echo "[OK] Maven installed"
fi
mvn -version | head -1
echo ""

# -------------------- PostgreSQL --------------------
if pg_isready -q 2>/dev/null; then
    echo "[OK] PostgreSQL is running"
else
    echo "[INSTALLING] PostgreSQL..."
    sudo apt-get install -y -qq postgresql postgresql-contrib
    sudo systemctl start postgresql
    sudo systemctl enable postgresql
    echo "[OK] PostgreSQL installed and started"
fi

echo "[DB] Setting up database..."
sudo -u postgres psql -tc "SELECT 1 FROM pg_database WHERE datname = '${DB_NAME}'" | grep -q 1 || \
    sudo -u postgres psql -c "CREATE DATABASE ${DB_NAME};"
sudo -u postgres psql -c "ALTER USER ${DB_USERNAME} PASSWORD '${DB_PASSWORD}';" 2>/dev/null
echo "[OK] Database '${DB_NAME}' ready"
echo ""

# -------------------- Build & Run --------------------
echo "=== Building the project ==="
mvn clean compile -q

echo ""
echo "=== Starting DSIP Backend on http://localhost:${SERVER_PORT:-8080} ==="
echo "    Press Ctrl+C to stop"
echo ""
mvn spring-boot:run
