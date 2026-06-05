# Gateway Service

The Gateway Service is the single public HTTP entry point for the LMS platform. It is built on Spring Cloud Gateway (reactive/WebFlux) and runs on port 8080. Every inbound request passes through a fixed chain of global filters — correlation-ID stamping, JWT authentication, and rate limiting — before being proxied to one of the downstream microservices (auth-service, content-service, or learning-service). The AI service is intentionally never exposed publicly; only the health probe of that service is surfaced through a path-rewrite route. Unauthenticated and over-limit requests are rejected at the gateway layer with a uniform JSON error body so downstream services never need to handle those cases themselves.

---

## Tech Stack

| Component | Artifact | Version |
|---|---|---|
| Language | Java | 17 |
| Build tool | Maven (spring-boot-maven-plugin) | — |
| Framework | Spring Boot | 3.2.5 |
| Gateway runtime | spring-cloud-starter-gateway (WebFlux/Reactor) | Spring Cloud 2023.0.3 |
| Actuator | spring-boot-starter-actuator | 3.2.5 |
| JWT library | jjwt-api / jjwt-impl / jjwt-jackson | 0.12.5 |
| Rate limiter | bucket4j-core (in-memory, no Redis) | 8.10.1 |
| API docs | springdoc-openapi-starter-webflux-ui | 2.5.0 |
| Test | spring-boot-starter-test + Mockito | 3.2.5 |

---

## Routing Table

Routes are declared in two places: `application.yml` (predicate-only routes) and `GatewayConfig` (programmatic routes with filters).

### YAML routes (`application.yml`)

| Route id | Path predicate | Upstream env var | Default upstream URL |
|---|---|---|---|
| `auth` | `/api/v1/auth/**`, `/api/v1/admin/**` | `AUTH_SERVICE_URL` | `http://localhost:8081` |
| `content` | `/api/v1/content/**` | `CONTENT_SERVICE_URL` | `http://localhost:8082` |
| `learning` | `/api/v1/learning/**` | `LEARNING_SERVICE_URL` | `http://localhost:8083` |

No path rewriting is applied on these routes; the full path is forwarded verbatim.

### Programmatic routes (`GatewayConfig`)

| Route id | Incoming path | Path rewritten to | Upstream env var | Default upstream URL |
|---|---|---|---|---|
| `ai-health` | `GET /api/v1/ai/health` | `/internal/ai/health` | `AI_SERVICE_URL` | `http://localhost:8084` |
| `avatar-static` | `GET /static/avatars/**` | (unchanged) | `AUTH_SERVICE_URL` | `http://localhost:8081` |

The `ai-health` route is the only route that rewrites the path. All other AI traffic is blocked at the network level (no route exists for it).

---

## Filter Chain

All filters implement `GlobalFilter` and run on every request. The execution order is determined by `Ordered.getOrder()`.

| Order value | Filter class | Responsibility |
|---|---|---|
| `-3` | `CorrelationIdFilter` | Correlation ID / request tracing |
| `-1` | `JwtAuthFilter` | JWT authentication and header injection |
| `0` | `RateLimitFilter` | Per-IP global rate limiting + per-endpoint caps |

### CorrelationIdFilter (order -3)

Runs first. Reads the `X-Request-Id` header from the incoming request. If the header is absent or blank, generates a new `UUID.randomUUID()` string. The (possibly new) ID is:

- Injected into the downstream request as `X-Request-Id`.
- Set on the response headers after the chain completes (in a `then(Mono.fromRunnable(...))` callback).

This ensures every log line — in the gateway and in downstream services — can be correlated by request ID.

### JwtAuthFilter (order -1)

Validates the `Authorization: Bearer <token>` header on every non-public request.

**Public paths (bypass JWT entirely):**

| Method | Path |
|---|---|
| `*` | `/actuator/**` |
| `*` | `/v3/api-docs/**`, `/swagger-ui/**`, `/webjars/**` |
| `GET` | `/api/v1/ai/health` |
| `GET` | `/static/avatars/**` |
| `POST` | `/api/v1/auth/register` |
| `POST` | `/api/v1/auth/login` |
| `POST` | `/api/v1/auth/refresh` |
| `POST` | `/api/v1/auth/forgot-password` |
| `POST` | `/api/v1/auth/reset-password` |

**For all other paths:**

1. If the `Authorization` header is missing or does not start with `Bearer `, returns `401 UNAUTHORIZED`.
2. Calls `JwtVerifier.parseAccess(token)`. If the token is invalid, expired, or of the wrong type (`typ != "access"`), returns `401 UNAUTHORIZED`.
3. On success, injects two headers into the mutated downstream request:
   - `X-User-Id` — the numeric user ID from the JWT subject claim.
   - `X-User-Role` — the value of the `role` claim.

The filter never forwards the raw `Authorization` header to downstream services, but it also does not strip it. Downstream services receive the original header alongside the injected ones.

### RateLimitFilter (order 0)

Uses Bucket4j in-memory token-bucket buckets stored in `ConcurrentHashMap` instances (no Redis dependency). Runs after `JwtAuthFilter` so `X-User-Id` is already available in the request headers when per-user checks execute.

The filter applies two distinct tiers of limiting on every request:

**Tier 1 — Per-endpoint sensitive caps (checked first):**

| Method | Path | Key | Bucket type | Default cap |
|---|---|---|---|---|
| `POST` | `/api/v1/auth/forgot-password` | Client IP | Hourly fixed window | 3 per IP per hour |
| `POST` | `/api/v1/content/courses/import` | `X-User-Id` header | Hourly fixed window | 5 per user per hour |
| `POST` | `/api/v1/content/ai/generate-*` (prefix) | `X-User-Id` header | Hourly fixed window | 20 per user per hour |

For the per-user buckets, if `X-User-Id` is not present in the headers the limit is skipped (the request falls through to the global check).

**Tier 2 — Global per-IP rate limit (always checked):**

Every request (including those that passed Tier 1) consumes one token from a per-IP greedy-refill bucket. Default: 20 requests per second per IP. The bucket refills greedily (tokens are added continuously as time passes), allowing short bursts up to the capacity.

**On limit exceeded:**
- HTTP `429 Too Many Requests`
- Header `Retry-After: 3600`
- Body: `{"code":"RATE_LIMIT_EXCEEDED","message":"Rate limit exceeded. Please try again later.","requestId":"<id>"}`

---

## Security Mechanism

### JWT Verification (`JwtVerifier`)

The gateway verifies tokens locally without calling auth-service on every request. The HMAC-SHA256 key is derived from the `JWT_SECRET` environment variable using the same three-step algorithm used by auth-service, ensuring the two services always agree on the signing key:

1. Try to Base64-decode the secret. If the decoded bytes are >= 32 bytes, use them as the key material.
2. If Base64 decoding fails or yields fewer than 32 bytes, take the raw UTF-8 bytes of the secret string. If those are >= 32 bytes, use them directly.
3. Otherwise, compute the SHA-256 hash of the UTF-8 bytes (always 32 bytes) and use that.

The final key bytes are passed to `Keys.hmacShaKeyFor()` (JJWT 0.12.5).

**Claims extracted and validated:**

| Claim | JWT field | Validation |
|---|---|---|
| Token type | `typ` | Must equal `"access"`; refresh tokens are explicitly rejected |
| User ID | `sub` (subject) | Parsed as `Long` |
| Role | `role` | String, forwarded as `X-User-Role` |
| Impersonator | `imp` | Optional `Long`; present during admin impersonation sessions |
| Expiry | `exp` | Enforced by JJWT parser |
| Signature | HMAC-SHA256 | Verified against derived key |

Any `JwtException` thrown by the parser (expired, wrong signature, wrong type) results in a `401` response.

### CORS (`CorsConfig` / `CorsProperties`)

A `CorsWebFilter` bean is registered globally. Configuration:

| Setting | Value |
|---|---|
| Allowed origin | Single origin from `FRONTEND_ORIGIN` env var |
| Allowed methods | `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS` |
| Allowed headers | `Authorization`, `Content-Type`, `X-Request-Id` |
| Allow credentials | `true` |
| Applies to | All paths (`/**`) |

Only one frontend origin is permitted. Wildcard origins are not supported.

### Error Handling (`GatewayErrorHandler`)

Implements `ErrorWebExceptionHandler` at `@Order(-1)`, which runs before Spring Boot's default `DefaultErrorWebExceptionHandler` (order 1). Catches unhandled exceptions produced by the gateway itself (no matching route, downstream connection failures, etc.) and returns a uniform JSON body:

```json
{"code": "<CODE>", "message": "<message>", "requestId": "<uuid>"}
```

**HTTP status to error code mapping:**

| HTTP Status | `code` field |
|---|---|
| 404 | `NOT_FOUND` |
| 401 | `UNAUTHORIZED` |
| 403 | `FORBIDDEN` |
| 400 | `BAD_REQUEST` |
| 429 | `RATE_LIMIT_EXCEEDED` |
| 503 | `SERVICE_UNAVAILABLE` |
| 504 | `GATEWAY_TIMEOUT` |
| 502 | `BAD_GATEWAY` |
| any other | `INTERNAL_ERROR` |

Human-readable messages are sanitized (quotes and backslashes escaped) before being embedded in the JSON string. Downstream service errors and connection failures produce a generic `"Downstream service is temporarily unavailable"` message rather than leaking internal details.

---

## Configuration

Spring Boot does **not** read `.env` files automatically. Variables must be exported into the process environment before starting the application.

### All environment variables

| Variable | `application.yml` key | Default | Required | Description |
|---|---|---|---|---|
| `SERVER_PORT` | `server.port` | `8080` | No | HTTP listening port |
| `JWT_SECRET` | `jwt.secret` | (insecure dev default — 93-char string) | **Yes in production** | HMAC signing secret; must match auth-service exactly |
| `AUTH_SERVICE_URL` | `auth.service.url` / route uri | `http://localhost:8081` | No | Base URL of auth-service (no trailing slash) |
| `CONTENT_SERVICE_URL` | route uri | `http://localhost:8082` | No | Base URL of content-service |
| `LEARNING_SERVICE_URL` | route uri | `http://localhost:8083` | No | Base URL of learning-service |
| `AI_SERVICE_URL` | `ai.service.url` | `http://localhost:8084` | No | Base URL of AI service (health probe only) |
| `FRONTEND_ORIGIN` | `cors.allowed-origin` | `http://localhost:3000` | No | Exact frontend origin for CORS |
| `RATE_LIMIT_RPS` | `rate-limit.requests-per-second` | `20` | No | Global per-IP requests per second |
| `RATE_LIMIT_FORGOT_PASSWORD_PER_IP_HOUR` | `rate-limit.forgot-password-per-ip-hour` | `3` | No | Max forgot-password requests per IP per hour |
| `RATE_LIMIT_IMPORT_PER_USER_HOUR` | `rate-limit.import-per-user-hour` | `5` | No | Max course import requests per user per hour |
| `RATE_LIMIT_AI_GENERATE_PER_USER_HOUR` | `rate-limit.ai-generate-per-user-hour` | `20` | No | Max AI content generation requests per user per hour |

### Actuator exposure

Only `health` and `info` endpoints are exposed via HTTP (`management.endpoints.web.exposure.include`).

### OpenAPI / Swagger

Enabled by default at:
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- Raw OpenAPI JSON: `http://localhost:8080/v3/api-docs`

---

## Tests

Tests use JUnit 5 (Jupiter), AssertJ, and Mockito. There is no Spring context loaded in any test class — all tests are plain unit tests that instantiate filter and verifier classes directly.

### `JwtVerifierTest`

**File:** `src/test/java/com/lms/gateway/security/JwtVerifierTest.java`

Covers `JwtVerifier.parseAccess()` in isolation. The test class builds real signed JWTs using JJWT and calls the verifier directly.

| Test method | What it covers |
|---|---|
| `parseAccess_validToken_returnsClaims` | Valid access token returns correct `userId` and `role`; `impersonatorId` is null |
| `parseAccess_tokenWithImpersonator_returnsImpersonatorId` | `imp` claim is extracted and returned correctly |
| `parseAccess_expiredToken_throwsJwtException` | Expired token raises `JwtException` |
| `parseAccess_tamperedSignature_throwsJwtException` | Token with last 4 chars replaced raises `JwtException` |
| `parseAccess_refreshToken_throwsJwtExceptionForWrongType` | Token with `typ=refresh` raises `JwtException` with message containing `"access"` |
| `parseAccess_randomString_throwsJwtException` | Arbitrary non-JWT string raises `JwtException` |

### `CorrelationIdFilterTest`

**File:** `src/test/java/com/lms/gateway/filter/CorrelationIdFilterTest.java`

Covers `CorrelationIdFilter` using `MockServerHttpRequest` / `MockServerWebExchange`.

| Test method | What it covers |
|---|---|
| `filter_noExistingRequestId_generatesUuid` | When `X-Request-Id` is absent, a non-blank UUID is generated and set on the downstream request |
| `filter_existingRequestId_preservesIt` | When `X-Request-Id` is already present, its value is preserved unchanged |
| `filter_orderIsMinusThree` | `getOrder()` returns `-3` |

### `JwtAuthFilterTest`

**File:** `src/test/java/com/lms/gateway/filter/JwtAuthFilterTest.java`

Covers `JwtAuthFilter` with a real `JwtVerifier` instance (no mocking of crypto). Uses `MockServerHttpRequest` / `MockServerWebExchange` and Mockito for the `GatewayFilterChain`.

| Test method | What it covers |
|---|---|
| `filter_publicPath_register_passesThrough` | `POST /api/v1/auth/register` is forwarded without token check |
| `filter_publicPath_login_passesThrough` | `POST /api/v1/auth/login` is forwarded without token check |
| `filter_publicPath_refresh_passesThrough` | `POST /api/v1/auth/refresh` is forwarded without token check |
| `filter_actuator_passesThrough` | `GET /actuator/health` is forwarded without token check |
| `filter_validToken_injectsXUserIdAndRole` | Valid Bearer token results in `X-User-Id` and `X-User-Role` injected into downstream request headers |
| `filter_missingAuthHeader_returns401` | Missing `Authorization` header returns 401; chain is never invoked |
| `filter_malformedAuthHeader_returns401` | `Authorization: Basic ...` (not `Bearer`) returns 401; chain is never invoked |
| `filter_expiredToken_returns401` | Expired access token returns 401; chain is never invoked |
| `filter_refreshTokenUsedAsAccess_returns401` | Token with `typ=refresh` used on a protected path returns 401 |
| `filter_orderIsMinusOne` | `getOrder()` returns `-1` |

---

## How to Run Locally

### Prerequisites

- Java 17 or later
- Maven 3.8 or later
- Downstream services running (or mocked) at the URLs in `.env`

### Setup

```powershell
Copy-Item .env.example .env
# Edit .env — set JWT_SECRET to the same value used in auth-service
```

### Run with Maven (PowerShell)

Spring Boot does not read `.env` automatically. Export variables first:

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

### Run with Maven (Bash / Linux)

```bash
export $(grep -v '^#' .env | xargs)
mvn spring-boot:run
```

### Run tests

```bash
mvn test
```

### Accessible endpoints after startup

| URL | Purpose |
|---|---|
| `http://localhost:8080` | API gateway root |
| `http://localhost:8080/actuator/health` | Health probe |
| `http://localhost:8080/swagger-ui/index.html` | Swagger UI |
| `http://localhost:8080/v3/api-docs` | Raw OpenAPI JSON |

---

## Docker

### Dockerfile details

The image uses a two-stage build:

| Stage | Base image | What happens |
|---|---|---|
| `build` | `eclipse-temurin:17-jdk-alpine` | Installs Maven via `apk`, runs `mvn -q -B package -DskipTests`, produces the fat JAR |
| Final | `eclipse-temurin:17-jre-alpine` | Copies only the JAR; creates a non-root user `spring:spring`; runs the app as that user |

Exposed port: `8080`.

### Build and run

```bash
docker build -t gateway-service:local .
docker run --rm -p 8080:8080 --env-file .env gateway-service:local
```

The `--env-file .env` flag is understood natively by Docker, so no manual variable export is needed.

---

## Headers Reference

### Headers injected by the gateway into downstream requests

| Header | Source | Present on |
|---|---|---|
| `X-Request-Id` | Generated UUID or forwarded from client | All requests |
| `X-User-Id` | JWT `sub` claim (as string) | All authenticated requests |
| `X-User-Role` | JWT `role` claim | All authenticated requests |

### Headers set on the gateway response

| Header | Value | Set by |
|---|---|---|
| `X-Request-Id` | Same value as the downstream request header | `CorrelationIdFilter` |
| `Retry-After` | `3600` | `RateLimitFilter` (on 429 only) |
| `Content-Type` | `application/json` | Error filters (on error responses) |

---

## Error Response Format

All error responses produced by the gateway (auth failures, rate limit, no route, downstream unavailable) share this JSON schema:

```json
{
  "code": "UNAUTHORIZED",
  "message": "Missing or malformed Authorization header",
  "requestId": "550e8400-e29b-41d4-a716-446655440000"
}
```

| HTTP status | `code` | Produced by |
|---|---|---|
| `401` | `UNAUTHORIZED` | `JwtAuthFilter` |
| `429` | `RATE_LIMIT_EXCEEDED` | `RateLimitFilter` |
| `404` | `NOT_FOUND` | `GatewayErrorHandler` |
| `502` | `BAD_GATEWAY` | `GatewayErrorHandler` |
| `503` | `SERVICE_UNAVAILABLE` | `GatewayErrorHandler` |
| `504` | `GATEWAY_TIMEOUT` | `GatewayErrorHandler` |
| `403` | `FORBIDDEN` | `GatewayErrorHandler` |
| `400` | `BAD_REQUEST` | `GatewayErrorHandler` |
| `500` | `INTERNAL_ERROR` | `GatewayErrorHandler` (fallback) |
