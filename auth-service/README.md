# Auth Service

LMS microservice: registration (always `STUDENT`), login, JWT access/refresh, `/me`, admin user management, impersonation with audit.

## Prerequisites

- **PostgreSQL 16+** with a database (e.g. `lms`).
- **Java 17+** and **Maven 3.8+** — for running on the JVM without Docker.
- **Docker** — optional, for container run.

## Configuration

1. Copy `.env.example` to `.env`.
2. Set at least `JWT_SECRET` (long random string or base64; see `JwtService` key rules), `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`. Optional: `SERVER_PORT`, `JWT_EXPIRY_SECONDS`, `JWT_REFRESH_EXPIRY_SECONDS`.

**Important:** Spring Boot does **not** read the `.env` file automatically. Values must be available as **OS environment variables** (IDE run config, shell export, or `docker run --env-file`). See `auth-service_DOC.md` for how this maps to `application.yml`.

Never commit `.env` (secrets).

## Run without Docker (Maven)

1. Start PostgreSQL and ensure the database exists.
2. From this directory (`app/auth-service`), load env vars, then:

```bash
mvn spring-boot:run
```

**IDE:** add the same keys as in `.env` to the run configuration’s environment variables, then run `AuthApplication`.

**PowerShell** (run from `app/auth-service`; loads `.env` into the current session, then Maven):

```powershell
Get-Content .\.env | ForEach-Object {
  if ($_ -match '^\s*#' -or $_ -match '^\s*$') { return }
  $k, $v = $_ -split '=', 2
  $k = $k.Trim()
  $v = $v.Trim().Trim("'").Trim('"')
  [Environment]::SetEnvironmentVariable($k, $v, "Process")
}
mvn spring-boot:run
```

**Bash:** export variables manually, use your own loader, or a tool like `direnv`. Maven only sees what the shell passes to the process.

## Run with Docker

Build and run from this directory (`app/auth-service`):

```bash
docker build -t auth-service:local .
docker run --rm -p 8081:8081 --env-file .env auth-service:local
```

- `docker run --env-file .env` injects variables into the container; Spring reads them like any other env.
- The image does **not** bundle `.env`; do not bake secrets into the image.
- **PostgreSQL on the host** (Windows/macOS Docker Desktop): inside the container `localhost` is not your PC. Use a JDBC URL such as  
  `jdbc:postgresql://host.docker.internal:5432/lms` in `.env` for that scenario.
- **PostgreSQL in Docker** on the same user-defined network: use the DB container hostname (e.g. `postgres`) in `DB_URL`, not `localhost`.

If you change `SERVER_PORT` in `.env`, adjust the publish flag (e.g. `-p 8082:8082`).

## URLs (default port 8081)

- API: `http://localhost:8081`
- Swagger UI: `http://localhost:8081/swagger-ui/index.html`
- Health: `http://localhost:8081/actuator/health`

## Endpoints

| Method | Path | Notes |
|--------|------|--------|
| POST | `/api/v1/auth/register` | Public; role `STUDENT` only |
| POST | `/api/v1/auth/login` | Returns access + refresh tokens |
| POST | `/api/v1/auth/refresh` | Body: `refreshToken` |
| GET | `/api/v1/auth/me` | Bearer access token |
| GET | `/api/v1/admin/users` | `ADMIN`; blocked while impersonating |
| PATCH | `/api/v1/admin/users/{id}` | Body: optional `role`, `active` |
| POST | `/api/v1/admin/impersonate` | `ADMIN`; returns access-only token for target |
| POST | `/api/v1/admin/impersonate/stop` | Bearer **impersonation** token; ends audit row |

Promote the first admin by SQL after registering (e.g. set `role_id` to `ADMIN`) or use a one-off SQL insert.

## Error shape

```json
{"code":"VALIDATION_ERROR","message":"...","requestId":"uuid"}
```

Include header `X-Request-Id` to correlate logs (optional; generated if absent).
