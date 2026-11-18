# SmartEdu Backend

## Prerequisites

- Java 21
- Maven 3.9+
- Microsoft SQL Server with the existing SmartEdu database
- SQL Server JDBC access account

## Environment variables

Copy `.env.example` to `.env` and provide real values. Do not commit database
credentials or JWT secrets.

| Variable | Description |
|---|---|
| `SMARTEDU_DB_URL` | SQL Server JDBC URL |
| `SMARTEDU_DB_USERNAME` | Database application account |
| `SMARTEDU_DB_PASSWORD` | Database password |
| `SMARTEDU_JPA_SHOW_SQL` | Optional SQL logging flag |
| `SMARTEDU_SERVER_PORT` | HTTP port, default `8080` |
| `SMARTEDU_CORS_ALLOWED_ORIGINS` | Comma-separated frontend origins |
| `SMARTEDU_JWT_SECRET` | JWT signing secret |
| `SMARTEDU_JWT_EXPIRATION_MS` | Access-token lifetime |

The application reads environment variables through `application.properties`.

## Run

```powershell
cd Be\demo
.\mvnw.cmd clean compile
.\mvnw.cmd spring-boot:run
```

Health endpoint: `http://localhost:8080/api/health`

Swagger UI: `http://localhost:8080/swagger-ui.html`

OpenAPI document: `http://localhost:8080/v3/api-docs`

## Architecture

The backend uses a modular-monolith structure. Shared cross-cutting concerns
are under `com.smartedu.common`. Business modules are under
`com.smartedu.modules`. Entity and repository implementation must follow the
existing SQL Server schema and must not be created before the schema is verified.

JPA is configured with `ddl-auto=none` and will not create or alter tables.
