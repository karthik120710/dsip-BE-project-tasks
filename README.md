# DSIP Backend

A production-ready Spring Boot application with Google OAuth2 authentication, PostgreSQL database, and whitelist-based access control.

## Features

- **Google OAuth2 Authentication**: Backend-handled OAuth2 Authorization Code Flow
- **Whitelist Access Control**: Only whitelisted emails can access the application
- **Server-side Sessions**: PostgreSQL-backed sessions using Spring Session JDBC
- **Session Management**: Configurable inactivity timeout (7 days) and maximum lifetime (2 months)
- **Multiple Device Support**: Users can have multiple concurrent sessions
- **Admin APIs**: Manage whitelisted emails via REST endpoints
- **Docker Ready**: Multi-stage Dockerfile and docker-compose for easy deployment

## Tech Stack

- Java 21
- Spring Boot 3.4.1
- Spring Security with OAuth2 Client
- Spring Data JPA (Hibernate)
- Spring Session JDBC
- PostgreSQL 16
- Docker

## Project Structure

```
src/main/java/com/dsip/backend/
├── DsipBackendApplication.java    # Main application entry point
├── HealthController.java          # Health check endpoints
├── config/
│   ├── AppProperties.java         # Application configuration properties
│   ├── SecurityConfig.java        # Spring Security configuration
│   ├── SessionConfig.java         # Session management configuration
│   └── GlobalExceptionHandler.java
├── auth/
│   ├── AuthController.java        # Authentication status endpoint
│   ├── OAuth2AuthenticationSuccessHandler.java
│   ├── OAuth2AuthenticationFailureHandler.java
│   ├── SessionService.java        # Session management service
│   └── SessionLifetimeFilter.java # Absolute session lifetime enforcement
├── user/
│   ├── User.java                  # User entity
│   ├── UserDto.java               # User data transfer object
│   ├── UserRepository.java        # User repository
│   ├── UserService.java           # User service
│   └── UserController.java        # User API endpoints
├── whitelist/
│   ├── WhitelistedEmail.java      # Whitelist entity
│   ├── WhitelistedEmailRepository.java
│   └── WhitelistService.java
└── admin/
    ├── AdminController.java       # Admin API endpoints
    ├── WhitelistDto.java
    └── AddWhitelistEmailRequest.java
```

## Environment Variables

### Required

| Variable | Description |
|----------|-------------|
| `GOOGLE_CLIENT_ID` | Google OAuth2 Client ID |
| `GOOGLE_CLIENT_SECRET` | Google OAuth2 Client Secret |

### Database

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `dsip` | Database name |
| `DB_USERNAME` | `postgres` | Database username |
| `DB_PASSWORD` | `postgres` | Database password |
| `DB_POOL_SIZE` | `10` | HikariCP max pool size |
| `DB_POOL_MIN_IDLE` | `5` | HikariCP min idle connections |

### Server

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8080` | Server port |
| `COOKIE_SECURE` | `true` | Secure cookie flag (set to `false` for local HTTP) |

### OAuth2 Redirects

| Variable | Default | Description |
|----------|---------|-------------|
| `OAUTH2_REDIRECT_URI` | `{baseUrl}/login/oauth2/code/{registrationId}` | OAuth2 callback URL |
| `OAUTH2_SUCCESS_REDIRECT_URL` | `http://localhost:3000` | Redirect after successful login |
| `OAUTH2_FAILURE_REDIRECT_URL` | `http://localhost:3000/auth/error` | Redirect after OAuth2 failure |
| `OAUTH2_WHITELIST_FAILURE_REDIRECT_URL` | `http://localhost:3000/auth/unauthorized` | Redirect for non-whitelisted users |

### Session

| Variable | Default | Description |
|----------|---------|-------------|
| `SESSION_INACTIVITY_TIMEOUT_DAYS` | `7` | Session inactivity timeout in days |
| `SESSION_MAX_LIFETIME_DAYS` | `60` | Maximum session lifetime in days |
| `SESSION_MAX_AGE` | `5184000` | Cookie max age in seconds (60 days) |

### CORS

| Variable | Default | Description |
|----------|---------|-------------|
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Comma-separated list of allowed origins |

### JPA

| Variable | Default | Description |
|----------|---------|-------------|
| `JPA_DDL_AUTO` | `validate` | Hibernate DDL auto mode |
| `JPA_SHOW_SQL` | `false` | Show SQL in logs |

### Logging

| Variable | Default | Description |
|----------|---------|-------------|
| `LOG_LEVEL_ROOT` | `INFO` | Root log level |
| `LOG_LEVEL_APP` | `DEBUG` | Application log level |
| `LOG_LEVEL_SECURITY` | `INFO` | Spring Security log level |
| `LOG_LEVEL_SESSION` | `INFO` | Spring Session log level |

## API Endpoints

### Public Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/` | Application info |
| GET | `/health` | Health check |
| GET | `/oauth2/authorization/google` | Initiate Google OAuth2 login |

### Protected Endpoints (require authentication)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/auth/status` | Get authentication status |
| GET | `/api/user/me` | Get current user info |
| GET | `/api/user/sessions/count` | Get active session count |
| POST | `/api/user/sessions/invalidate-all` | Invalidate all sessions |
| GET | `/logout` | Logout current session |

### Admin Endpoints (require authentication)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/admin/whitelist` | Get all whitelisted emails |
| POST | `/api/admin/whitelist` | Add email to whitelist |
| DELETE | `/api/admin/whitelist/{email}` | Remove email from whitelist |
| GET | `/api/admin/whitelist/check/{email}` | Check if email is whitelisted |

## Getting Started

### Prerequisites

- Java 21+
- Docker and Docker Compose (for containerized deployment)
- Google Cloud Console project with OAuth2 credentials

### Google OAuth2 Setup

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select existing one
3. Navigate to "APIs & Services" > "Credentials"
4. Create OAuth 2.0 Client ID (Web application)
5. Add authorized redirect URI: `http://localhost:8080/login/oauth2/code/google`
6. Copy Client ID and Client Secret

### Local Development

1. Clone the repository

2. Create `.env` file from example:
   ```bash
   cp .env.example .env
   ```

3. Update `.env` with your Google OAuth2 credentials

4. Start with Docker Compose:
   ```bash
   docker-compose up -d
   ```

5. Add your email to whitelist (connect to PostgreSQL and run):
   ```sql
   INSERT INTO whitelisted_emails (email) VALUES ('your-email@gmail.com');
   ```

6. Access the application at `http://localhost:8080`

### Manual Build

```bash
# Build
./mvnw clean package -DskipTests

# Run (ensure PostgreSQL is running and configured)
java -jar target/dsip-backend-1.0.0.jar
```

## Database Schema

The application requires the following tables (automatically created via `schema.sql`):

- `SPRING_SESSION` - Session storage
- `SPRING_SESSION_ATTRIBUTES` - Session attributes
- `users` - Application users
- `whitelisted_emails` - Email whitelist

## Authentication Flow

1. Frontend redirects user to `/oauth2/authorization/google`
2. User authenticates with Google
3. Backend receives OAuth2 callback
4. `OAuth2AuthenticationSuccessHandler`:
   - Extracts email from Google profile
   - Checks if email is in whitelist
   - If NOT whitelisted: invalidates session, redirects to error URL
   - If whitelisted: creates/updates user, sets session lifetime, redirects to success URL
5. Session cookie is set with HTTPOnly, Secure, SameSite=Lax flags

## Session Management

- **Inactivity Timeout**: Session expires after 7 days of inactivity (configurable)
- **Sliding Expiration**: Each request extends the session
- **Maximum Lifetime**: Sessions expire after 60 days regardless of activity (configurable)
- **Multiple Devices**: Users can have multiple concurrent sessions
- **Logout**: Clears session and cookies; use `/api/user/sessions/invalidate-all` to logout from all devices

## Security Features

- HTTPOnly cookies prevent XSS attacks
- Secure flag ensures cookies are only sent over HTTPS
- SameSite=Lax provides CSRF protection
- API endpoints return 401 Unauthorized (no redirect to login)
- Whitelist validation before session creation
- Server-side session storage in PostgreSQL

## Production Deployment

1. Set `COOKIE_SECURE=true`
2. Use HTTPS
3. Set strong database password
4. Configure proper CORS origins
5. Set `JPA_DDL_AUTO=validate` (use migrations for schema changes)
6. Configure proper logging levels
7. Set up database backups for session and user data

## License

MIT
