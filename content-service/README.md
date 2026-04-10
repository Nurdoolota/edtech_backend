# content-service

Content management microservice for the LMS platform.

Owns: `courses`, `tasks`, `groups`, `user_groups`, `group_courses` tables.

## Responsibilities

- CRUD for courses and their tasks (all 6 task types with JSONB validation)
- CRUD for student groups and group-course assignments
- Role-based access: STUDENT (read-only, own groups), TEACHER (own content), ADMIN (full access)

## Running locally

```bash
cp .env.example .env
# edit .env with real DB credentials and JWT_SECRET matching auth-service
mvn spring-boot:run
```

## Running with Docker

Containers on the same user-defined network reach each other by **service name**, not `localhost`. See [`../DOCKER.md`](../DOCKER.md) for the full multi-service layout.

1. **Build the image** (from this directory):

   ```bash
   docker build -t content-service:local .
   ```

2. **Use the same network** as auth and gateway (e.g. `lms-net`):

   ```bash
   docker network create lms-net   # once, if it does not exist
   ```

3. **Configure `.env`** for container runtime:
   - `DB_URL` — if PostgreSQL runs in Docker on `lms-net`, use the DB container hostname, e.g. `jdbc:postgresql://postgres:5432/lms`. If Postgres is on the host (Windows/macOS), often `jdbc:postgresql://host.docker.internal:5432/lms`.
   - `JWT_SECRET` — **must match** `auth-service` and `gateway-service`.

4. **Run** (expose **8082**, fixed name for gateway upstream):

   ```bash
   docker run --rm --name content-service --network lms-net -p 8082:8082 --env-file .env content-service:local
   ```

5. **Gateway** — in `gateway-service/.env` set (no trailing slash):

   ```env
   CONTENT_SERVICE_URL=http://content-service:8082
   ```

   Restart the gateway container after changing env. Traffic from the browser still goes to the gateway on port **8080**; it proxies `/api/v1/content/**` to this service.

## API

All endpoints are prefixed `/api/v1/content/` and routed through the API Gateway.

| Method | Path | Role |
|--------|------|------|
| GET/POST | `/courses` | TEACHER+, STUDENT(GET) |
| GET/PUT/DELETE | `/courses/{id}` | TEACHER(own), ADMIN |
| GET/POST | `/courses/{id}/topics` | TEACHER(own), STUDENT(via group) |
| GET/POST | `/tasks` | TEACHER+, STUDENT(GET) |
| PUT/DELETE | `/tasks/{id}` | TEACHER(own), ADMIN |
| GET/POST | `/groups` | TEACHER+, ADMIN |
| PUT/DELETE | `/groups/{id}` | TEACHER(own), ADMIN |
| POST | `/groups/{id}/students` | TEACHER(own), ADMIN |
| DELETE | `/groups/{id}/students/{userId}` | TEACHER(own), ADMIN |
| POST | `/groups/{id}/courses` | TEACHER(own), ADMIN |
| DELETE | `/groups/{id}/courses/{courseId}` | TEACHER(own), ADMIN |

Swagger UI: `http://localhost:8082/swagger-ui.html`

## Notes

- DB schema is managed by `auth-service` Flyway migrations (`V2__create_content_learning_schema.sql`).
- This service connects to the same PostgreSQL database with `spring.flyway.enabled=false`.
- JWT_SECRET must match the value used by auth-service.
