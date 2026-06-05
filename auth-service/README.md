# Auth Service

Auth Service is the authentication and user-management microservice for the LMS English Learning Platform. It handles registration, login, JWT access/refresh token issuance and rotation, full user profile management (avatar, bio, locale, timezone, notification preferences), password reset via a 6-digit email code, email address change with a 6-digit confirmation code, admin user management, admin impersonation with full audit trail, and a proxied read-only view of the AI call log from ai-service. The service is the single Flyway schema owner for the shared PostgreSQL database used by all platform microservices.

---

## Table of contents

1. [Tech stack](#tech-stack)
2. [REST endpoints](#rest-endpoints)
3. [Request / response shapes](#request--response-shapes)
4. [Services and responsibilities](#services-and-responsibilities)
5. [Security and authentication](#security-and-authentication)
6. [Configuration and environment variables](#configuration-and-environment-variables)
7. [Database: tables and entities](#database-tables-and-entities)
8. [Flyway migrations](#flyway-migrations)
9. [Email subsystem](#email-subsystem)
10. [Tests](#tests)
11. [How to run locally](#how-to-run-locally)
12. [Docker build and run](#docker-build-and-run)
13. [Well-known URLs](#well-known-urls)
14. [Error shape](#error-shape)

---

## Tech stack

Taken directly from `pom.xml`:

| Component | Version / artifact |
|---|---|
| Java | 17 |
| Spring Boot | 3.2.5 (parent) |
| Spring Security | included in Boot |
| Spring Data JPA / Hibernate | included in Boot |
| Spring Boot Actuator | included in Boot |
| Spring Boot Validation (Jakarta) | included in Boot |
| Spring Boot Mail | included in Boot |
| Thymeleaf (email templates) | included in Boot |
| Flyway Core | managed by Boot |
| PostgreSQL JDBC driver | managed by Boot (runtime) |
| JJWT (jjwt-api / jjwt-impl / jjwt-jackson) | 0.12.5 |
| SpringDoc OpenAPI (Swagger UI) | 2.5.0 |
| SendGrid Java SDK | 4.10.2 |
| Spring Boot Test + spring-security-test | included in Boot (test scope) |
| Maven build plugin | spring-boot-maven-plugin |

---

## REST endpoints

### Auth controller — `/api/v1/auth`

| Method | Path | Auth required | Role restriction | Notes |
|--------|------|---------------|------------------|-------|
| POST | `/api/v1/auth/register` | No | — | Registers a new user; role is always `STUDENT` |
| POST | `/api/v1/auth/login` | No | — | Returns access + refresh tokens |
| POST | `/api/v1/auth/refresh` | No | — | Rotates refresh token; returns new access + refresh pair |
| GET | `/api/v1/auth/me` | Yes (Bearer) | Any authenticated | Returns current user profile from JWT |
| PATCH | `/api/v1/auth/me` | Gateway injects `X-User-Id` | Any | Partial profile update |
| POST | `/api/v1/auth/change-password` | Gateway injects `X-User-Id` | Any | Requires current password |
| DELETE | `/api/v1/auth/me` | Gateway injects `X-User-Id` | Any | Soft-delete with password confirmation |
| POST | `/api/v1/auth/me/avatar` | Gateway injects `X-User-Id` | Any | Upload PNG/JPEG avatar (multipart/form-data) |
| POST | `/api/v1/auth/forgot-password` | No | — | Sends 6-digit reset code to registered email |
| POST | `/api/v1/auth/reset-password` | No | — | Verifies code and sets a new password |
| POST | `/api/v1/auth/change-email` | Gateway injects `X-User-Id` | Any | Sends 6-digit confirmation code to the new email |
| POST | `/api/v1/auth/confirm-email` | Gateway injects `X-User-Id` | Any | Confirms email change with 6-digit code |
| GET | `/api/v1/auth/students/by-email` | Yes (Bearer) | `TEACHER` or `ADMIN` | Find a student by email |

### Admin user controller — `/api/v1/admin/users`

All routes require role `ADMIN`. Impersonation tokens cannot call these endpoints (403 returned).

| Method | Path | Notes |
|--------|------|-------|
| GET | `/api/v1/admin/users` | Paginated user list; optional `?role=STUDENT\|TEACHER\|ADMIN` filter |
| GET | `/api/v1/admin/users/{id}` | Get a single user by ID |
| PATCH | `/api/v1/admin/users/{id}` | Change `role` and/or `active` flag |

### Admin impersonation controller — `/api/v1/admin`

| Method | Path | Auth | Notes |
|--------|------|------|-------|
| POST | `/api/v1/admin/impersonate` | Bearer (ADMIN, not already impersonating) | Starts impersonation; returns an access-only token for the target user |
| POST | `/api/v1/admin/impersonate/stop` | Bearer (impersonation token) | Ends impersonation; sets `ended_at` on audit row |

### Admin AI log controller — `/api/v1/admin`

| Method | Path | Auth | Notes |
|--------|------|------|-------|
| GET | `/api/v1/admin/ai-log` | Bearer (ADMIN) | Proxied from ai-service `/internal/ai/log`; params: `page`, `size`, `userId`, `status` |

### Static / infrastructure

| Path | Notes |
|------|-------|
| `GET /static/avatars/**` | Public static file serving of saved avatar images |
| `GET /actuator/health` | Health check (public) |
| `GET /actuator/info` | Info (public) |
| `GET /swagger-ui/index.html` | Swagger UI (public) |
| `GET /v3/api-docs/**` | OpenAPI spec (public) |

---

## Request / response shapes

### `POST /api/v1/auth/register`

Request:
```json
{
  "email": "alice@example.com",
  "password": "secret1234",
  "firstName": "Alice",
  "lastName": "Smith"
}
```
Constraints: `email` — valid RFC email; `password` — 8–128 chars; `firstName`/`lastName` — non-blank, max 100 chars.

Response `201 Created`: `UserResponse` (see below).

---

### `POST /api/v1/auth/login`

Request:
```json
{ "email": "alice@example.com", "password": "secret1234" }
```

Response `200`:
```json
{
  "accessToken": "<jwt>",
  "refreshToken": "<jwt>",
  "tokenType": "Bearer",
  "expiresInSeconds": 900
}
```

---

### `POST /api/v1/auth/refresh`

Request:
```json
{ "refreshToken": "<jwt>" }
```

Response `200`: same shape as login (`TokenResponse` with both tokens).

---

### `GET /api/v1/auth/me`

Header: `Authorization: Bearer <access-token>`

Response `200`: `UserResponse` (see below).

---

### `PATCH /api/v1/auth/me`

Header: `X-User-Id: <long>` (injected by gateway)

Request (all fields optional):
```json
{
  "firstName": "Ivan",
  "lastName": "Petrov",
  "bio": "English teacher",
  "locale": "en",
  "timezone": "Europe/Moscow",
  "emailPrivate": true,
  "notifications": "{}"
}
```
Constraints: `firstName`/`lastName` max 100 chars; `bio` max 500 chars.

Response `200`: updated `UserResponse`.

---

### `POST /api/v1/auth/change-password`

Header: `X-User-Id: <long>`

Request:
```json
{ "currentPassword": "oldPass", "newPassword": "newPass99" }
```
Constraints: `newPassword` — 8–128 chars.

Response `204 No Content`.

---

### `DELETE /api/v1/auth/me`

Header: `X-User-Id: <long>`

Request:
```json
{ "password": "currentPassword" }
```

Response `204 No Content`. The user is soft-deleted: `is_active` is set to `false` and `email` is overwritten with `deleted-{id}@deleted.invalid`.

---

### `POST /api/v1/auth/me/avatar`

Header: `X-User-Id: <long>`

Request: `multipart/form-data`, field name `file`. Accepts `image/png` and `image/jpeg`, maximum size controlled by `AVATAR_MAX_SIZE_MB` (default 2 MB).

Response `200`:
```json
{ "avatarUrl": "/static/avatars/42.png" }
```

The file is saved to `AVATAR_STORAGE_PATH/{userId}.{ext}` and served at `/static/avatars/`.

---

### `POST /api/v1/auth/forgot-password`

Request:
```json
{ "email": "alice@example.com" }
```

Response `200`:
```json
{ "sent": true }
```
If the email is not registered, the response is still `{"sent": true}` (anti-enumeration). A new code cannot be requested more than once per minute per user (HTTP 429 if too soon).

---

### `POST /api/v1/auth/reset-password`

Request:
```json
{ "email": "alice@example.com", "code": "123456", "newPassword": "newPass99" }
```
Constraints: `newPassword` — 8–128 chars.

Response `200`:
```json
{ "reset": true }
```
Errors: 400 (invalid/expired code), 429 (too many attempts, max 5).

---

### `POST /api/v1/auth/change-email`

Header: `X-User-Id: <long>`

Request:
```json
{ "newEmail": "new@example.com", "password": "currentPassword" }
```

Response `200`:
```json
{ "sent": true }
```
A 6-digit code is sent to `newEmail`. The code expires in 15 minutes. Any previously pending change for this user is deleted first.

---

### `POST /api/v1/auth/confirm-email`

Header: `X-User-Id: <long>`

Request:
```json
{ "code": "123456" }
```
Constraints: exactly 6 characters.

Response `200`:
```json
{ "emailChanged": true }
```

---

### `GET /api/v1/auth/students/by-email`

Header: `Authorization: Bearer <token>` (TEACHER or ADMIN)

Query parameter: `?email=alice@example.com`

Response `200`: `UserResponse`.

---

### `GET /api/v1/admin/users`

Query parameters: `?role=STUDENT` (optional), Spring Data `Pageable` params (`page`, `size`, `sort`; default page size 20).

Response `200`:
```json
{
  "content": [ /* UserResponse[] */ ],
  "totalElements": 42,
  "totalPages": 3
}
```

---

### `GET /api/v1/admin/users/{id}`

Response `200`: `UserResponse`.

---

### `PATCH /api/v1/admin/users/{id}`

Request (at least one field must be present):
```json
{ "role": "TEACHER", "active": true }
```
`role` values: `STUDENT`, `TEACHER`, `ADMIN`.

Response `200`: updated `UserResponse`.

---

### `POST /api/v1/admin/impersonate`

Request:
```json
{ "targetUserId": 7 }
```

Response `200`:
```json
{
  "accessToken": "<jwt with imp claim>",
  "tokenType": "Bearer",
  "expiresInSeconds": 900
}
```
`refreshToken` is `null` (impersonation tokens are access-only). Cannot target another admin or a disabled user.

---

### `POST /api/v1/admin/impersonate/stop`

No body. Must be called with the impersonation access token.

Response `204 No Content`.

---

### `GET /api/v1/admin/ai-log`

Query parameters: `page` (default 0), `size` (default 50), `userId` (optional), `status` (optional: `SUCCESS`, `ERROR`, `TIMEOUT`).

Response `200`:
```json
{
  "content": [
    {
      "id": 1,
      "requestId": "uuid",
      "userId": 42,
      "endpoint": "/ai/chat",
      "model": "gpt-4o",
      "latencyMs": 1200,
      "tokensIn": 300,
      "tokensOut": 150,
      "status": "SUCCESS",
      "error": null,
      "createdAt": "2025-05-01T10:00:00Z"
    }
  ],
  "totalElements": 100,
  "totalPages": 2,
  "page": 0,
  "size": 50
}
```

---

### `UserResponse` shape (used in many responses)

```json
{
  "id": 42,
  "email": "alice@example.com",
  "firstName": "Alice",
  "lastName": "Smith",
  "displayName": "Alice Smith",
  "role": "STUDENT",
  "active": true,
  "avatarUrl": "/static/avatars/42.png",
  "bio": "Learning English",
  "locale": "ru",
  "timezone": "UTC",
  "emailPrivate": false,
  "notifications": "{}",
  "lastLoginAt": "2025-05-01T10:00:00Z",
  "updatedAt": "2025-05-01T10:00:00Z",
  "createdAt": "2025-01-01T00:00:00Z"
}
```

---

## Services and responsibilities

### `AuthService`

Core authentication logic.

- `register(RegisterRequest)` — checks for email uniqueness (case-insensitive), BCrypt-hashes the password, assigns the `STUDENT` role, persists the user, returns `UserResponse`.
- `login(LoginRequest)` — looks up user by email (case-insensitive), verifies password with BCrypt, rejects disabled accounts, updates `last_login_at`, issues both access and refresh tokens.
- `refresh(RefreshRequest)` — parses and validates the refresh JWT, loads the user, rejects disabled accounts, issues a new token pair.
- `me(Authentication)` — reads the user id from `JwtUserPrincipal`, loads the user from the DB, returns `UserResponse`.
- `findStudentByEmail(String)` — finds a user by email who has role `STUDENT`; used by teachers and admins.
- `updateProfile(Long, ProfileUpdateRequest)` — applies only non-null fields from the request (partial update), sets `updated_at`.
- `changePassword(Long, String, String)` — verifies current password, BCrypt-hashes the new one, saves.
- `deleteAccount(Long, DeleteAccountRequest)` — verifies password, sets `is_active = false`, replaces email with `deleted-{id}@deleted.invalid`, sets `updated_at`.

### `AdminUserService`

Admin-only user management.

- `list(RoleName, Pageable)` — returns a paginated `PagedUsersResponse`; uses `@EntityGraph` to avoid N+1 on the role.
- `getById(long)` — loads a single user with its role; throws `NOT_FOUND` if absent.
- `patch(long, AdminUserPatchRequest)` — changes `role` and/or `active` flag; requires at least one field.

### `ImpersonationService`

Admin impersonation with audit.

- `start(Authentication, ImpersonateRequest)` — validates that the caller is not already impersonating; checks that the target exists, is not an admin, and is active; creates an `ImpersonationAudit` row (`started_at`, `ip_address`); issues an access-only JWT with the `imp` claim set to the admin's ID.
- `stop(Authentication)` — validates the token is an impersonation token (`imp` claim present); finds the open audit row by `admin_id` + `target_user_id` where `ended_at IS NULL`; sets `ended_at = now()`.

### `PasswordResetService`

6-digit code-based password reset.

- `requestReset(String email)` — anti-enumeration: returns silently for unknown emails; enforces a 1-minute rate-limit between requests; generates a cryptographically random 6-digit code with `SecureRandom`, BCrypt-hashes it, saves a `PasswordResetCode`, sends the `reset_code` email.
- `resetPassword(String, String, String)` — finds the latest unused code for the user; checks `attempts < maxAttempts` (default 5); checks `expires_at` (default 15 min); BCrypt-matches the code; on success sets `used_at`, updates the password hash.

### `EmailChangeService`

Two-step email address change.

- `requestChange(Long userId, String newEmail, String password)` — verifies current password; checks uniqueness of the new email; generates a 6-digit code; deletes all existing pending records for this user; saves a new `PendingEmailChange` (expires in 15 minutes); sends the `email_change_code` email to the new address.
- `confirmChange(Long userId, String code)` — finds the latest pending record; checks expiry; BCrypt-matches the code; re-checks that the new email is still available; sets the user's email; deletes the pending record.

### `AvatarService`

Avatar file upload.

- `save(Long userId, MultipartFile file)` — validates MIME type (`image/png`, `image/jpeg`); validates file size against `AVATAR_MAX_SIZE_MB`; determines extension; creates the storage directory if absent; writes the file to `{storagePath}/{userId}.{ext}`; returns the public URL `/static/avatars/{userId}.{ext}`.

### `AiLogClient`

HTTP proxy client for the AI call log.

- `getAiLog(int page, int size, Long userId, String status)` — calls `GET {AI_SERVICE_URL}/internal/ai/log` with query parameters using `RestTemplate` (connect timeout 5 s, read timeout 10 s); throws `ApiBusinessException(SERVICE_UNAVAILABLE)` on `RestClientException`.

### `UserMapper`

Stateless utility that converts a `User` entity to a `UserResponse` DTO. Computes `displayName` as `firstName + " " + lastName`.

### `StaticResourceConfig`

Implements `WebMvcConfigurer`. Registers a Spring MVC resource handler that serves avatar files:

- URL pattern: `/static/avatars/**`
- Filesystem location: `file:{avatar.storage-path}/` (resolved from `AvatarProperties.storagePath`)

The trailing slash is appended automatically if absent. This is what makes `GET /static/avatars/{userId}.{ext}` work without a servlet container — Spring MVC streams the file directly from the configured directory.

### `RestTemplateConfig`

Creates the default `RestTemplate` bean injected into `AiLogClient`:

- Connect timeout: **5 000 ms**
- Read timeout: **10 000 ms**

Uses `SimpleClientHttpRequestFactory`.

---

## Security and authentication

### JWT mechanism

- Library: **JJWT 0.12.5** (`io.jsonwebtoken`).
- Algorithm: **HMAC-SHA** (key derived from `JWT_SECRET`).
- Key resolution (`JwtService.resolveKey`):
  1. Attempt to Base64-decode the secret; if decoded bytes >= 32, use them as the HMAC key.
  2. Otherwise use the UTF-8 bytes directly if >= 32, or SHA-256-stretch them if shorter.
- Two token types distinguished by the `typ` claim:
  - **Access token** — contains `sub` (user ID as string), `email`, `role`, `typ=access`, `iat`, `exp`; optionally `imp` (impersonator admin ID for impersonation sessions).
  - **Refresh token** — contains `sub`, `typ=refresh`, `iat`, `exp` only.
- Impersonation tokens carry no refresh token (`refreshToken` is `null`).

### JWT filter (`JwtAuthenticationFilter`)

`OncePerRequestFilter` placed before `UsernamePasswordAuthenticationFilter`.

Bypassed (no filter applied) for:
- `GET /actuator/**`
- `/v3/api-docs/**`, `/swagger-ui/**`
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`

For all other requests: reads `Authorization: Bearer <token>`, parses it as an access token, populates `SecurityContextHolder`. Returns `{"code":"UNAUTHORIZED",...}` JSON on `JwtException`.

### Spring Security rules (`SecurityConfig`)

| Matcher | Policy |
|---------|--------|
| `GET /actuator/**` | `permitAll` |
| `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` | `permitAll` |
| `GET /static/avatars/**` | `permitAll` |
| `POST /api/v1/auth/register`, `POST /api/v1/auth/login` | `permitAll` |
| `POST /api/v1/auth/refresh` | `permitAll` |
| `POST /api/v1/auth/me/avatar` | `permitAll` (gateway handles auth) |
| `PATCH /api/v1/auth/me` | `permitAll` (gateway handles auth) |
| `DELETE /api/v1/auth/me` | `permitAll` (gateway handles auth) |
| `POST /api/v1/auth/change-password` | `permitAll` (gateway handles auth) |
| `POST /api/v1/auth/forgot-password`, `POST /api/v1/auth/reset-password` | `permitAll` |
| `POST /api/v1/auth/change-email`, `POST /api/v1/auth/confirm-email` | `permitAll` (gateway handles auth) |
| `POST /api/v1/admin/impersonate/stop` | `authenticated` |
| `/api/v1/admin/**` | `hasRole("ADMIN")` |
| All other requests | `authenticated` |

Session policy: `STATELESS` (no HTTP sessions). CSRF disabled.

Password hashing: **BCrypt** (`BCryptPasswordEncoder`).

### Request ID filter (`RequestIdFilter`)

Highest-precedence `OncePerRequestFilter`. Reads `X-Request-Id` header (or generates a UUID if absent), stores it in `MDC` under key `requestId`, echoes it back in the response `X-Request-Id` header. All error responses include `requestId` from MDC.

### `JwtUserPrincipal`

Implements `UserDetails`. Fields: `userId`, `email`, `role` (`RoleName`), `impersonatorId` (null for real sessions). `getAuthorities()` returns `ROLE_{STUDENT|TEACHER|ADMIN}`. `isImpersonating()` returns `true` when `impersonatorId != null`.

---

## Configuration and environment variables

Spring Boot does **not** automatically read a `.env` file. Variables must be present as OS environment variables before starting the process (shell export, IDE run configuration, or `docker run --env-file`).

### Full variable reference

| Env var | `application.yml` property | Default | Description |
|---------|---------------------------|---------|-------------|
| `SERVER_PORT` | `server.port` | `8081` | HTTP port |
| `DB_URL` | `spring.datasource.url` | `jdbc:postgresql://localhost:5432/lms` | JDBC URL |
| `DB_USERNAME` | `spring.datasource.username` | `postgres` | DB user |
| `DB_PASSWORD` | `spring.datasource.password` | `qwerty` | DB password |
| `JWT_SECRET` | `jwt.secret` | *(weak default — override in production)* | HMAC key (plain string or Base64; must resolve to ≥32 bytes) |
| `JWT_EXPIRY_SECONDS` | `jwt.access-expiry-seconds` | `900` (15 min) | Access token TTL |
| `JWT_REFRESH_EXPIRY_SECONDS` | `jwt.refresh-expiry-seconds` | `604800` (7 days) | Refresh token TTL |
| `AVATAR_MAX_SIZE_MB` | `avatar.max-size-mb` | `2` | Maximum avatar file size in MB |
| `AVATAR_STORAGE_PATH` | `avatar.storage-path` | `./data/avatars` | Directory where avatar files are stored |
| `AI_SERVICE_URL` | `ai-service.url` | `http://localhost:8084` | Base URL of ai-service for log proxy |
| — | `email.provider` | `console` | Email backend: `console`, `smtp`, `sendgrid` |
| — | `email.from` | `noreply@lms-english.local` | Sender address |
| — | `email.frontend-url` | `http://localhost:3000` | Used in email template links |
| — | `email.smtp-host` | *(none)* | SMTP host (smtp provider only) |
| — | `email.smtp-port` | `587` | SMTP port |
| — | `email.smtp-user` | *(none)* | SMTP username |
| — | `email.smtp-password` | *(none)* | SMTP password |
| — | `email.smtp-use-tls` | `true` | STARTTLS |
| — | `email.sendgrid-api-key` | *(none)* | SendGrid API key |
| — | `password-reset.code-expire-minutes` | `15` | Reset code TTL in minutes |
| — | `password-reset.max-attempts` | `5` | Max wrong attempts before lockout |

Additional Spring Boot configuration (from `application.yml`, not exposed as vars):

- `spring.jpa.hibernate.ddl-auto: validate` — Hibernate does not create or alter tables; Flyway owns the schema.
- `spring.jpa.open-in-view: false` — lazy loading only within transactions.
- `hibernate.jdbc.time_zone: UTC` — all timestamps stored in UTC.
- `spring.servlet.multipart.max-file-size: 10MB` / `max-request-size: 10MB` — multipart upload ceiling.
- `management.endpoints.web.exposure.include: health,info` — only these actuator endpoints are exposed over HTTP.

---

## Database: tables and entities

The service defines five JPA entities (owned by this service) plus manages the complete schema for all microservices via Flyway.

### `roles` — entity `Role`

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | `BIGSERIAL` | PK |
| `name` | `VARCHAR(32)` | NOT NULL, UNIQUE, CHECK IN (`STUDENT`, `TEACHER`, `ADMIN`) |

Pre-seeded by V1 migration with all three roles.

### `users` — entity `User`

| Column | Type | Constraints / default |
|--------|------|-----------------------|
| `id` | `BIGSERIAL` | PK |
| `email` | `VARCHAR(255)` | NOT NULL, UNIQUE |
| `password_hash` | `VARCHAR(255)` | NOT NULL |
| `first_name` | `VARCHAR(100)` | NOT NULL |
| `last_name` | `VARCHAR(100)` | NOT NULL |
| `role_id` | `BIGINT` | NOT NULL, FK → `roles(id)` ON DELETE RESTRICT |
| `is_active` | `BOOLEAN` | NOT NULL, DEFAULT TRUE |
| `created_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT NOW() |
| `avatar_url` | `VARCHAR(500)` | nullable |
| `bio` | `VARCHAR(500)` | nullable |
| `locale` | `VARCHAR(8)` | NOT NULL, DEFAULT `'ru'` |
| `timezone` | `VARCHAR(64)` | NOT NULL, DEFAULT `'UTC'` |
| `email_private` | `BOOLEAN` | NOT NULL, DEFAULT FALSE |
| `notifications` | `JSONB` | NOT NULL, DEFAULT `'{}'` |
| `last_login_at` | `TIMESTAMPTZ` | nullable |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT NOW() |

Indexes: `idx_users_role_id` on `role_id`.

### `impersonation_audit` — entity `ImpersonationAudit`

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | `BIGSERIAL` | PK |
| `admin_id` | `BIGINT` | NOT NULL, FK → `users(id)` ON DELETE CASCADE |
| `target_user_id` | `BIGINT` | NOT NULL, FK → `users(id)` ON DELETE CASCADE |
| `started_at` | `TIMESTAMPTZ` | NOT NULL |
| `ended_at` | `TIMESTAMPTZ` | nullable (null = session still open) |
| `ip_address` | `VARCHAR(45)` | nullable |

Indexes: `idx_impersonation_admin_id`, `idx_impersonation_target_user_id`, partial index `idx_impersonation_open` where `ended_at IS NULL`.

### `password_reset_codes` — entity `PasswordResetCode`

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | `BIGSERIAL` | PK |
| `user_id` | `BIGINT` | NOT NULL, FK → `users(id)` ON DELETE CASCADE |
| `code_hash` | `VARCHAR(255)` | NOT NULL (BCrypt hash of 6-digit code) |
| `expires_at` | `TIMESTAMPTZ` | NOT NULL |
| `used_at` | `TIMESTAMPTZ` | nullable (null = not yet used) |
| `attempts` | `INT` | NOT NULL, DEFAULT 0 |
| `created_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT NOW() |

Index: `idx_password_reset_codes_user_id`.

### `pending_email_changes` — entity `PendingEmailChange`

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | `BIGSERIAL` | PK |
| `user_id` | `BIGINT` | NOT NULL, FK → `users(id)` ON DELETE CASCADE |
| `new_email` | `VARCHAR(255)` | NOT NULL |
| `code_hash` | `VARCHAR(255)` | NOT NULL (BCrypt hash of 6-digit code) |
| `expires_at` | `TIMESTAMPTZ` | NOT NULL |
| `created_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT NOW() |

Index: `idx_pending_email_changes_user_id`.

### Tables owned by other services (schema created here via Flyway)

The following tables are created by auth-service Flyway migrations but are read/written by other microservices:

`prompt_templates`, `courses`, `groups`, `user_groups`, `group_courses`, `tasks`, `task_results`, `topics`, `lessons`, `lesson_blocks`, `course_teachers`, `lesson_access`, `ai_call_log`.

---

## Flyway migrations

Three migration files under `src/main/resources/db/migration/`. Flyway is enabled and reads from `classpath:db/migration`.

### V1 — `V1__create_roles_users_impersonation.sql`

- Creates `roles`, `users`, `impersonation_audit` tables with all indexes.
- Seeds the `roles` table with `STUDENT`, `TEACHER`, `ADMIN`.

### V2 — `V2__create_content_learning_schema.sql`

- Creates `prompt_templates` (AI prompt storage with `code`, `body`, `temperature`, `max_tokens`).
- Creates `courses` (title, description, author FK).
- Creates `groups`, `user_groups`, `group_courses` (group-based course access).
- Creates `tasks` (course FK, JSONB `content`, optional `prompt_template_id`; types: `FILL_BLANKS`, `TRUE_FALSE`, `VIDEO`, `TEXT`, `TRANSLATION`, `DEBATES`).
- Creates `task_results` (student FK, JSONB `answer_content`, `ai_feedback`, `score`; statuses: `SUBMITTED`, `CHECKED`, `VALIDATED_BY_TEACHER`).

### V3 — `V3__add_profile_and_content_hierarchy.sql`

- Adds profile columns to `users`: `avatar_url`, `bio`, `locale`, `timezone`, `email_private`, `notifications`, `last_login_at`, `updated_at`.
- Adds `level`, `publish_mode`, `access_status` columns to `courses` with check constraints.
- Creates `password_reset_codes` and `pending_email_changes`.
- Creates `topics`, `lessons`, `lesson_blocks` (course content hierarchy).
- Creates `course_teachers` (co-teacher roles: `OWNER`, `EDITOR`).
- Creates `lesson_access` (per-student lesson unlock grants).
- Extends `tasks` with `lesson_id` (NOT NULL after backfill), `order_index`, `title`, `status`, `ai_generated`, `generation_metadata`, `unlock_mode`, `prerequisite_task_id`, `required_score`; adds new task types (`LISTENING`, `SPEAKING`, `READING_COMPREHENSION`, `IMAGE_DESCRIPTION`); adds status and unlock check constraints.
- Extends `task_results` with `ai_score`, `ai_breakdown`, `teacher_score`, `teacher_feedback`, `transcript`, `media_id`.
- Creates `ai_call_log` (id, request_id, user_id FK, endpoint, model, latency_ms, tokens_in, tokens_out, status, error, created_at; statuses: `SUCCESS`, `ERROR`, `TIMEOUT`).
- Backfills existing tasks into the new hierarchy (creates a "Default" topic + "Default Lesson" per course, assigns orphaned tasks).

---

## Email subsystem

The email provider is selected at startup via `email.provider` (default `console`). Selection is done with `@ConditionalOnProperty` in `EmailConfig`.

### Providers

| Value | Class | Behavior |
|-------|-------|----------|
| `console` (default) | `ConsoleEmailService` | Renders the text template and logs the result with `INFO`; no actual delivery |
| `smtp` | `SmtpEmailService` | Sends multipart MIME email (HTML + text) via Spring's `JavaMailSender` |
| `sendgrid` | `SendGridEmailService` | Sends HTML-only email via the SendGrid Java SDK |

### Email types

| Method | Subject | Templates |
|--------|---------|-----------|
| `sendResetCode` | "Password Reset Code — LMS English" | `emails/reset_code.html`, `emails/reset_code.txt` |
| `sendPasswordChangedNotice` | "Your Password Has Been Changed — LMS English" | `emails/password_changed.html`, `emails/password_changed.txt` |
| `sendEmailChangeCode` | "Confirm Your New Email — LMS English" | `emails/email_change_code.html`, `emails/email_change_code.txt` |

Templates are Thymeleaf templates in `src/main/resources/templates/`. Two separate `TemplateEngine` instances are used (one for HTML, one for TEXT mode). Variables passed to templates:

- `reset_code`: `displayName`, `code`, `expiresMinutes` (15), `frontendUrl`
- `password_changed`: `displayName`, `frontendUrl`
- `email_change_code`: `displayName`, `code`, `newEmail`, `expiresMinutes` (15), `frontendUrl`

---

## Tests

All tests are pure unit tests using **JUnit 5** + **Mockito** (`@ExtendWith(MockitoExtension.class)`). No Spring context, no database required.

### `JwtServiceTest`

`src/test/java/com/lms/auth/security/JwtServiceTest.java`

Tests `JwtService` in isolation (no mocks — uses the real service with test properties).

| Test | What it verifies |
|------|-----------------|
| `resolveKey_longAsciiSecret_doesNotThrow` | Long ASCII secret is accepted without exception |
| `resolveKey_shortAsciiSecret_doesNotThrow` | Short secret is SHA-256 stretched and accepted |
| `resolveKey_validBase64Secret_doesNotThrow` | Base64-encoded secret with ≥32 decoded bytes is accepted |
| `resolveKey_urlSafeBase64Chars_doesNotThrow` | URL-safe characters fall back to UTF-8 path without error |
| `createAccessToken_parseBack_hasExpectedClaims` | Round-trip: issued access token parses back to the correct `userId` and `role`; `impersonatorId` is null |
| `createAccessToken_withImpersonator_impersonatorIdPreserved` | Impersonator ID survives serialisation/deserialisation |
| `parseAccessPrincipal_rejectsRefreshToken` | Access-token parser throws `JwtException` on a refresh token |
| `parseRefreshUserId_rejectsAccessToken` | Refresh-token parser throws `JwtException` on an access token |
| `issueTokens_withoutImpersonation_returnsBothTokens` | Normal login produces both access and refresh tokens |
| `issueTokens_withImpersonation_refreshTokenIsNull` | Impersonation issues access token only; refresh is null |

### `AuthServiceTest`

`src/test/java/com/lms/auth/service/AuthServiceTest.java`

Covers FR-001 (registration), FR-002 (login), FR-003 (refresh), FR-004 (/me), plus profile update, password change, and account deletion.

| Test | What it verifies |
|------|-----------------|
| `register_rejectsDuplicateEmail` | Duplicate email → `ApiBusinessException(CONFLICT)` |
| `register_success_assignsStudentRole` | Successful registration normalises email to lowercase and assigns STUDENT |
| `register_missingStudentRole_throwsIllegalState` | Missing role seed row → `IllegalStateException` |
| `login_success_returnsBothTokens` | Valid credentials → both tokens returned; email matching is case-insensitive |
| `login_unknownEmail_unauthorized` | Unknown email → `UNAUTHORIZED` |
| `login_wrongPassword_unauthorized` | Wrong password → `UNAUTHORIZED` |
| `login_inactiveAccount_forbidden` | Disabled account → `FORBIDDEN` |
| `refresh_success_returnsNewTokens` | Valid refresh token → new token pair |
| `refresh_invalidToken_unauthorized` | `JwtException` from `JwtService` → `UNAUTHORIZED` |
| `refresh_inactiveAccount_forbidden` | Disabled account during refresh → `FORBIDDEN` |
| `me_returnsCurrentUser` | `me()` loads user by principal's user ID |
| `me_userNotFound` | Non-existent principal user → `NOT_FOUND` |
| `updateProfile_success_appliesNonNullFields` | Null fields are skipped; non-null fields are applied |
| `updateProfile_userNotFound` | Unknown user ID → `NOT_FOUND` |
| `deleteAccount_success_setsInactive` | Correct password soft-deletes: `active=false`, email anonymised |
| `deleteAccount_wrongPassword_unauthorized` | Wrong password → `UNAUTHORIZED`; user unchanged |
| `changePassword_success_updatesHash` | Correct current password → hash updated |
| `changePassword_wrongCurrentPassword_unauthorized` | Wrong current password → `UNAUTHORIZED` |
| `changePassword_userNotFound` | Unknown user ID → `NOT_FOUND` |

### `ImpersonationServiceTest`

`src/test/java/com/lms/auth/service/ImpersonationServiceTest.java`

| Test | What it verifies |
|------|-----------------|
| `start_rejectsIfAlreadyImpersonating` | Cannot nest impersonation sessions → `FORBIDDEN` |
| `start_rejectsTargetAdmin` | Cannot impersonate another admin → `FORBIDDEN` |
| `start_rejectsInactiveTarget` | Cannot impersonate a disabled user → `FORBIDDEN` |
| `start_success_savesAudit` | Happy path: audit row saved with correct `admin`, `targetUser`, non-null `startedAt`; access token returned |
| `stop_rejectsNonImpersonationToken` | Token without `imp` claim → `BAD_REQUEST` |
| `stop_success_setsEndedAt` | Open audit row found → `ended_at` set to now |
| `stop_noActiveAudit_notFound` | No open audit row → `NOT_FOUND` |

### `PasswordResetServiceTest`

`src/test/java/com/lms/auth/service/PasswordResetServiceTest.java`

| Test | What it verifies |
|------|-----------------|
| `requestReset_unknownEmail_returnsQuietly` | Anti-enumeration: unknown email → no exception, no email sent |
| `requestReset_noPriorCode_savesAndSendsEmail` | No prior code → new code saved, email dispatched |
| `requestReset_recentCodeExists_throws429` | Code created < 1 min ago → HTTP 429 |
| `requestReset_oldCodeExists_allowsNewCode` | Code created > 1 min ago → new code allowed |
| `resetPassword_unknownEmail_throws400` | Unknown email → 400 |
| `resetPassword_noCode_throws400` | No active code → 400 |
| `resetPassword_tooManyAttempts_throws429` | Attempts >= maxAttempts → 429 |
| `resetPassword_expiredCode_throws400` | Expired code → 400 |
| `resetPassword_wrongCode_incrementsAttemptsAndThrows400` | Wrong code → `attempts++`, 400 |
| `resetPassword_correctCode_updatesPasswordAndMarksUsed` | Correct code → password updated, `used_at` set |

### `EmailChangeServiceTest`

`src/test/java/com/lms/auth/service/EmailChangeServiceTest.java`

| Test | What it verifies |
|------|-----------------|
| `requestChange_userNotFound_throws404` | Unknown user → 404 |
| `requestChange_wrongPassword_throws401` | Wrong password → 401 |
| `requestChange_emailAlreadyTaken_throws409` | New email already registered → 409 |
| `requestChange_valid_deletesOldAndSavesAndSendsEmail` | Happy path: old pending deleted, new record saved, email sent to new address |
| `requestChange_normalizesEmailToLowercase` | Input email normalised to lowercase before saving and sending |
| `confirmChange_noPendingRecord_throws400` | No pending record → 400 |
| `confirmChange_expiredCode_throws400` | Expired code → 400 |
| `confirmChange_wrongCode_throws400` | Wrong code → 400 |
| `confirmChange_emailConflict_throws409` | New email taken between request and confirm → 409 |
| `confirmChange_valid_updatesEmailAndDeletesPending` | Correct code → email updated, pending record deleted |

---

## How to run locally

### Prerequisites

- **PostgreSQL 16+** with an existing database (e.g. `lms`).
- **Java 17+** and **Maven 3.8+**.

### Steps

1. Copy `.env.example` to `.env` and fill in at minimum `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `JWT_SECRET` (a long random string).
2. Load the environment variables into the shell session.
3. Run from the `app/auth-service` directory:

```bash
mvn spring-boot:run
```

Flyway runs automatically on startup and creates / migrates the schema.

**PowerShell** — load `.env` then start Maven:

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

**IDE (IntelliJ / VS Code):** add the same key=value pairs to the run configuration's environment variables section and run `AuthApplication.main()`.

**Bash:** use `export VAR=value` statements, `direnv`, or a wrapper script before calling Maven.

To promote the first admin: register normally (gets role `STUDENT`), then update the `role_id` column directly in SQL to point to the `ADMIN` role.

---

## Docker build and run

The `Dockerfile` uses a two-stage build:

- **Stage 1** (`eclipse-temurin:17-jdk-alpine`): installs Maven, builds the jar with `-DskipTests`.
- **Stage 2** (`eclipse-temurin:17-jre-alpine`): runs as a non-root user `spring:spring`. Exposes port `8081`.

```bash
# Build from app/auth-service directory
docker build -t auth-service:local .

# Run (loads variables from .env file)
docker run --rm -p 8081:8081 --env-file .env auth-service:local
```

Notes:
- The `.env` file is **not** baked into the image; `--env-file` injects at runtime.
- If PostgreSQL runs on the **Docker Desktop host** (Windows/macOS), use `host.docker.internal` in `DB_URL`, e.g. `jdbc:postgresql://host.docker.internal:5432/lms`.
- If PostgreSQL runs in Docker on the **same user-defined network**, use its container name as the hostname.
- If `SERVER_PORT` differs from `8081`, update the `-p` flag accordingly.

---

## Well-known URLs (default port 8081)

| URL | Description |
|-----|-------------|
| `http://localhost:8081` | API base |
| `http://localhost:8081/swagger-ui/index.html` | Swagger UI |
| `http://localhost:8081/v3/api-docs` | OpenAPI JSON |
| `http://localhost:8081/actuator/health` | Health check |
| `http://localhost:8081/actuator/info` | Service info |
| `http://localhost:8081/static/avatars/{userId}.{ext}` | User avatar files |

---

## Error shape

All errors (including auth failures and validation) follow this shape:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "email: must be a well-formed email address",
  "requestId": "550e8400-e29b-41d4-a716-446655440000"
}
```

`requestId` is taken from the `X-Request-Id` request header if provided, or auto-generated as a UUID. The same value is echoed back in the response `X-Request-Id` header.

Common error codes:

| Code | HTTP status | Meaning |
|------|-------------|---------|
| `UNAUTHORIZED` | 401 | Missing, invalid, or expired token; wrong credentials |
| `FORBIDDEN` | 403 | Authenticated but not allowed (wrong role, impersonation restriction) |
| `NOT_FOUND` | 404 | Resource does not exist |
| `CONFLICT` | 409 | Unique constraint violation (e.g. email already registered) |
| `VALIDATION_ERROR` | 400 | Bean validation or constraint violation failed |
| `BAD_REQUEST` | 400 | Invalid request (wrong code, expired code, etc.) |
| `TOO_MANY_REQUESTS` | 429 | Rate limit or too many wrong attempts |
| `SERVICE_UNAVAILABLE` | 503 | Downstream service (ai-service) unreachable |
| `INTERNAL_ERROR` | 500 | Unexpected server error |
