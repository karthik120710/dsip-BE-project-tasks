@echo off
setlocal enabledelayedexpansion

echo ============================================================
echo  DSIP Backend - Local Setup Script (Windows)
echo ============================================================
echo.

:: -------------------- Load .env --------------------
if not exist .env (
    echo [ERROR] .env file not found. Copy .env.example and fill in your values:
    echo         copy .env.example .env
    echo.
    pause
    exit /b 1
)

echo [ENV] Loading .env file...
for /f "usebackq tokens=1,* delims==" %%A in (".env") do (
    set "line=%%A"
    if not "!line:~0,1!"=="#" if not "%%A"=="" (
        set "%%A=%%B"
        echo     %%A=%%B
    )
)
echo [OK] Environment loaded from .env
echo.

:: -------------------- Check winget --------------------
where winget >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] winget not found. Install App Installer from Microsoft Store.
    echo         https://aka.ms/getwinget
    pause
    exit /b 1
)

:: -------------------- Java 21 --------------------
java -version 2>&1 | findstr /i "21" >nul 2>&1
if %errorlevel% equ 0 (
    echo [OK] Java 21 already installed
) else (
    echo [INSTALLING] Java 21 ^(Eclipse Temurin^)...
    winget install EclipseAdoptium.Temurin.21.JDK --accept-package-agreements --accept-source-agreements
    echo [WARN] Restart this terminal after installation for JAVA_HOME to take effect.
    pause
    exit /b 0
)
java -version 2>&1 | findstr /i "version"
echo.

:: -------------------- Maven --------------------
where mvn >nul 2>&1
if %errorlevel% equ 0 (
    echo [OK] Maven already installed
) else (
    echo [INSTALLING] Maven...
    winget install Apache.Maven --accept-package-agreements --accept-source-agreements
    echo [WARN] Restart this terminal after installation for mvn to be on PATH.
    pause
    exit /b 0
)
echo.

:: -------------------- PostgreSQL --------------------
where psql >nul 2>&1
if %errorlevel% equ 0 (
    echo [OK] PostgreSQL already installed
) else (
    echo [INSTALLING] PostgreSQL 16...
    winget install PostgreSQL.PostgreSQL.16 --accept-package-agreements --accept-source-agreements
    echo [WARN] During install, set superuser password to match DB_PASSWORD in your .env
    echo [WARN] Restart this terminal after installation.
    pause
    exit /b 0
)

:: -------------------- Create Database --------------------
echo [DB] Setting up database...
set "PGPASSWORD=!DB_PASSWORD!"
psql -h !DB_HOST! -p !DB_PORT! -U !DB_USERNAME! -tc "SELECT 1 FROM pg_database WHERE datname = '!DB_NAME!'" 2>nul | findstr "1" >nul 2>&1
if %errorlevel% neq 0 (
    psql -h !DB_HOST! -p !DB_PORT! -U !DB_USERNAME! -c "CREATE DATABASE !DB_NAME!;" 2>nul
    echo [OK] Database '!DB_NAME!' created
) else (
    echo [OK] Database '!DB_NAME!' already exists
)
echo.

:: -------------------- Build & Run --------------------
echo === Building the project ===
call mvn clean compile -q
if %errorlevel% neq 0 (
    echo [ERROR] Build failed
    pause
    exit /b 1
)

echo.
echo === Starting DSIP Backend on http://localhost:!SERVER_PORT! ===
echo     Press Ctrl+C to stop
echo.
call mvn spring-boot:run

pause
