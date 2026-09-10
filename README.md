# Poetry Site

## Local development environment

This project uses Docker Compose for its local PostgreSQL database and Mailpit test inbox.

1. The local `.env` file is created during setup and is ignored by Git. To recreate it from the safe template if needed:

   ```bash
   cp .env.example .env
   ```

2. Edit `.env` and replace `POSTGRES_PASSWORD` with a unique local password.

3. Validate the configuration and start services:

   ```bash
   docker compose config
   docker compose up -d
   docker compose ps
   ```

4. Open Mailpit at http://localhost:8025.

PostgreSQL is available only from this computer at `localhost:5432`.
Mailpit SMTP is available only from this computer at `localhost:1025`.

## Manual API testing

The local `.env` activates the `local` Spring profile, which enables Swagger UI only on your computer.

1. Start the local services, then start the application:

   ```bash
   ./mvnw spring-boot:run
   ```

2. Open http://localhost:8080/swagger-ui.html. The raw OpenAPI description is at http://localhost:8080/v3/api-docs.

Swagger and its OpenAPI endpoints are disabled by default. Set `SWAGGER_ENABLED=true` only while you need manual API testing, then remove it or set it to `false`. Do not set `SPRING_PROFILES_ACTIVE=local` in production.

## Stopping services

```bash
docker compose down
```

This preserves the named PostgreSQL volume and its data.

## Test database isolation

Automated tests must never use this Compose PostgreSQL database. Spring integration tests will use Testcontainers to create an isolated temporary PostgreSQL container for each test run. Unit tests will not require a database.
