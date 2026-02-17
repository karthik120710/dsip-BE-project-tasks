Set-Location "D:\projects\DSIP-Backend"

# Set environment variables
$env:GOOGLE_CLIENT_ID = "dummy-client-id"
$env:GOOGLE_CLIENT_SECRET = "dummy-client-secret"
$env:DB_HOST = "localhost"
$env:DB_PORT = "5432"
$env:DB_NAME = "dsip"
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "postgres"

# Run the application
.\run-maven.cmd spring-boot:run
