# Gateway Service

API Gateway — single public HTTP entry point for the LMS platform.

Routes all `/api/v1/...` traffic to downstream services after JWT validation.  
AI Service is **never** routed publicly (called internally by Learning Service only).

## Prerequisites

- **Java 17+** and **Maven 3.8+**
- Running downstream services (or mocks) at the URLs configured via env vars

## Configuration

1. Copy `.env.example` to `.env`.
2. Fill in `JWT_SECRET` (must match the value used in `auth-service`), service URLs, `FRONTEND_ORIGIN`.

Spring Boot does **not** read `.env` automatically — export vars before running. See PowerShell snippet below.

## Run without Docker (Maven)

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

## Run with Docker

```bash
docker build -t gateway-service:local .
docker run --rm -p 8080:8080 --env-file .env gateway-service:local
```

## URLs (default port 8080)

- API: `http://localhost:8080`
- Health: `http://localhost:8080/actuator/health`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`

## Routes

| Path prefix         | Downstream           |
|---------------------|----------------------|
| `/api/v1/auth/**`    | `AUTH_SERVICE_URL`   |
| `/api/v1/content/**` | `CONTENT_SERVICE_URL`|
| `/api/v1/learning/**`| `LEARNING_SERVICE_URL`|

## Public paths (no JWT required)

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `GET /actuator/**`
- `GET /swagger-ui/**`, `GET /v3/api-docs/**`

## Headers injected by Gateway

| Header         | Value                          |
|----------------|--------------------------------|
| `X-Request-Id` | Generated UUID (or forwarded)  |
| `X-User-Id`    | User ID extracted from JWT     |
| `X-User-Role`  | Role extracted from JWT        |

## Error format

```json
{"code": "UNAUTHORIZED", "message": "...", "requestId": "uuid"}
```

| HTTP status | code                  |
|-------------|-----------------------|
| 401         | `UNAUTHORIZED`        |
| 429         | `RATE_LIMIT_EXCEEDED` |
| 404         | `NOT_FOUND`           |
| 500         | `INTERNAL_ERROR`      |
