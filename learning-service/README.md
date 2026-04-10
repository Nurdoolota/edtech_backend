# learning-service

Core learning flow microservice for the LMS platform.

## Responsibilities

- Receive student answer submissions for all 6 task types
- Select and execute the correct evaluation strategy (Strategy pattern)
- Store results in `task_results`
- Serve result history to students and teachers
- Teacher grade validation endpoint

## Architecture

### Strategy Pattern

```
TaskCheckerFactory
  ├── FILL_BLANKS → KeyBasedChecker   (compares against content.answers)
  ├── TRUE_FALSE  → KeyBasedChecker   (compares against content.correctOptions)
  ├── TEXT        → AiBasedChecker    → POST /internal/ai/evaluate
  ├── TRANSLATION → AiBasedChecker
  ├── VIDEO       → AiBasedChecker
  └── DEBATES     → AiBasedChecker
```

### Access Check (Content Service Contract)

Before evaluating any submission, Learning Service verifies student access via:

```
GET ${CONTENT_SERVICE_URL}/internal/learning/access?studentId={id}&taskId={id}
Response: { "hasAccess": true | false }
```

The Content Service is responsible for implementing this endpoint. It checks:
1. The task belongs to a course
2. The course is assigned to a group
3. The student is a member of that group

If the endpoint returns `hasAccess: false`, or returns 4xx, a `403 Forbidden` is returned to the client.

### DEBATES Rule

A single submit sends the full chat history as a JSON array in `answer_content`.
One `task_results` row per student per task (enforced by DB UNIQUE constraint).
Once a teacher validates a result (`VALIDATED_BY_TEACHER`), the student cannot resubmit.

## API Endpoints

| Method | Path | Role | Description |
|--------|------|------|-------------|
| POST | `/api/v1/learning/tasks/{taskId}/submit` | STUDENT | Submit answer |
| GET | `/api/v1/learning/my-results?courseId=` | STUDENT | Own result history |
| GET | `/api/v1/learning/tasks/{taskId}/results` | TEACHER | All results (paginated) |
| PATCH | `/api/v1/learning/results/{resultId}/validate` | TEACHER | Validate/override score |

## Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `SERVER_PORT` | No | `8083` | HTTP port |
| `DB_URL` | Yes | — | PostgreSQL JDBC URL |
| `DB_USERNAME` | Yes | — | DB user |
| `DB_PASSWORD` | Yes | — | DB password |
| `JWT_SECRET` | Yes | — | Shared secret (must match auth-service) |
| `AI_SERVICE_URL` | No | `http://localhost:8084` | Internal AI Service URL |
| `CONTENT_SERVICE_URL` | No | `http://localhost:8082` | Content Service URL |

Copy `.env.example` to `.env` and fill in the required values.

## Local Development

```bash
# Prerequisites: PostgreSQL running with schema from auth-service migrations
cp .env.example .env
# Edit .env with your local values
mvn spring-boot:run
```

Swagger UI: http://localhost:8083/swagger-ui.html

## Running with Docker

Build the image from this directory (requires Docker with BuildKit):

```bash
cd learning-service
docker build -t lms-learning-service:latest .
```

Run the container. Pass the same environment variables as for local runs; `JWT_SECRET` must match `auth-service`, and `DB_URL` must point to a reachable PostgreSQL instance (often `host.docker.internal` on Docker Desktop when the DB runs on the host).

```bash
docker run --rm -p 8083:8083 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/lms \
  -e DB_USERNAME=postgres \
  -e DB_PASSWORD=yourpassword \
  -e JWT_SECRET=your_shared_jwt_secret \
  -e CONTENT_SERVICE_URL=http://host.docker.internal:8082 \
  -e AI_SERVICE_URL=http://host.docker.internal:8084 \
  lms-learning-service:latest
```

- **Health:** `GET http://localhost:8083/actuator/health`
- **Port mapping:** container listens on `8083` (`SERVER_PORT` defaults to 8083; override with `-e SERVER_PORT=...` if you change the image `EXPOSE` and `-p` mapping).
- **Linux:** if `host.docker.internal` is unavailable, use the host gateway IP or run services on the same Docker network and pass service names as URLs (e.g. `http://content-service:8082`).

Optional: load variables from a file (do not commit real secrets):

```bash
docker run --rm -p 8083:8083 --env-file .env lms-learning-service:latest
```

## Database

This service does NOT own DB migrations. All schema changes are managed by `auth-service`
via Flyway (`V2__create_content_learning_schema.sql`).

Write access: `task_results` table only.
Read access: `tasks`, `courses`, `user_groups`, `group_courses` (read-only, no writes).

## Tests

```bash
mvn test
```

Test coverage:
- `KeyBasedCheckerTest` — FILL_BLANKS and TRUE_FALSE evaluation logic
- `AiBasedCheckerTest` — AI Service HTTP call with mocked RestClient
- `LearningServiceTest` — submit/validate flow with mocked dependencies
- `LearningControllerTest` — RBAC enforcement (student/teacher role restrictions)
