# learning-service

The learning-service is the core answer-submission and evaluation microservice for the LMS English Learning Platform. It receives student answers for all six supported task types, selects and executes the appropriate evaluation strategy (key-based comparison for objective tasks, AI-delegated scoring for open-ended tasks), enforces student access and task-unlock rules, stores results in the `task_results` table, and exposes result-history endpoints for both students and teachers. It also handles student audio/media file uploads (WebM, WAV) for video and debates tasks, and exposes internal endpoints consumed by other services in the platform.

---

## Tech Stack

| Component | Library / Version |
|---|---|
| Runtime | Java 17 |
| Framework | Spring Boot 3.2.5 |
| Web | spring-boot-starter-web |
| Security | spring-boot-starter-security |
| Persistence | spring-boot-starter-data-jpa + Hibernate |
| Database | PostgreSQL (runtime driver) |
| Validation | spring-boot-starter-validation |
| JWT | jjwt-api / jjwt-impl / jjwt-jackson 0.12.5 |
| API docs | springdoc-openapi-starter-webmvc-ui 2.5.0 |
| Actuator | spring-boot-starter-actuator |
| Build | Maven (spring-boot-maven-plugin) |
| Test | spring-boot-starter-test + spring-security-test |

---

## REST Endpoints

### Public (authenticated) API — prefix `/api/v1/learning`

#### POST `/api/v1/learning/tasks/{taskId}/submit`

**Role:** `STUDENT`

Submit a student answer for a task. The service verifies access, checks the unlock gate, evaluates the answer, and upserts the `task_results` row.

**Path variable:** `taskId` — Long

**Request body:**
```json
{
  "answerContent": <any JSON value>,
  "mediaId": "optional-uuid.webm"
}
```

- `answerContent` — required; flexible JSON (string, array, object) whose shape depends on task type.
- `mediaId` — optional; previously uploaded media file identifier (from the upload-audio endpoint).

**Response `200 OK`:**
```json
{
  "id": 42,
  "taskId": 1,
  "studentId": 10,
  "answerContent": "<JSON serialized as string>",
  "aiFeedback": "Good answer.",
  "aiScore": 85,
  "aiBreakdown": "{\"score\":85,\"feedback\":\"Good answer.\",\"verdict\":\"Accepted\"}",
  "teacherScore": null,
  "teacherFeedback": null,
  "score": 85.00,
  "transcript": null,
  "mediaId": null,
  "status": "CHECKED",
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:00Z"
}
```

**Error responses:**
- `403 FORBIDDEN` — student does not have access to the task's course, or the task is locked
- `403 TASK_LOCKED` — task is locked by `SEQUENTIAL` or `PREREQUISITE` unlock mode
- `404 NOT_FOUND` — task does not exist
- `409 CONFLICT` — result was already validated by a teacher; resubmission is blocked
- `400 VALIDATION_ERROR` — `answerContent` is null/blank or malformed JSON
- `503 SERVICE_UNAVAILABLE` — Content Service or AI Service is unreachable

---

#### GET `/api/v1/learning/my-results`

**Role:** `STUDENT`

Returns the authenticated student's result history (most-recent first), optionally filtered by course.

**Query parameters:**

| Parameter | Type | Required | Default | Description |
|---|---|---|---|---|
| `courseId` | Long | No | — | Filter results to tasks belonging to this course |
| `page` | int | No | `0` | Zero-based page number |
| `size` | int | No | `20` | Page size |

**Response `200 OK`:**
```json
{
  "content": [ <TaskResultResponse>, ... ],
  "totalElements": 42,
  "totalPages": 3
}
```

---

#### GET `/api/v1/learning/tasks/{taskId}/results`

**Role:** `TEACHER`

Returns all student results for a specific task, paginated and sorted by `createdAt` descending.

**Path variable:** `taskId` — Long

**Query parameters:** `page` (default `0`), `size` (default `20`)

**Response `200 OK`:**
```json
{
  "content": [ <TaskResultResponse>, ... ],
  "totalElements": 12,
  "totalPages": 1
}
```

**Error responses:**
- `404 NOT_FOUND` — task does not exist

---

#### PATCH `/api/v1/learning/results/{resultId}/validate`

**Role:** `TEACHER`

Override or validate the score for a result. Sets `teacher_score`, `teacher_feedback`, recalculates `score` as `max(ai_score, teacher_score)`, and transitions `status` to `VALIDATED_BY_TEACHER`.

**Path variable:** `resultId` — Long

**Request body:**
```json
{
  "score": 95,
  "comment": "Excellent work!"
}
```

- `score` — required; `BigDecimal` in the range `[0, 100]`
- `comment` — optional; teacher feedback text

**Response `200 OK`:** `TaskResultResponse` with `status: "VALIDATED_BY_TEACHER"`

**Error responses:**
- `404 NOT_FOUND` — result does not exist
- `422 VALIDATION_ERROR` — score is outside `[0, 100]`

---

#### POST `/api/v1/learning/tasks/{taskId}/upload-audio`

**Role:** `STUDENT`

Upload an audio file (WebM or WAV) for a task submission. The file is saved to the local media storage path and a `mediaId` token is returned.

**Path variable:** `taskId` — Long

**Request:** `multipart/form-data`, field name `file`

**Constraints:**
- Accepted MIME types: `audio/webm`, `audio/wav`
- Maximum file size: 20 MB (Spring multipart limit: 20 MB request, 22 MB total)

**Response `201 Created`:**
```json
{
  "mediaId": "550e8400-e29b-41d4-a716-446655440000.webm"
}
```

**Error responses:**
- `400 INVALID_MEDIA_TYPE` — content type is not `audio/webm` or `audio/wav`
- `400 FILE_TOO_LARGE` — file exceeds 20 MB
- `500 STORAGE_ERROR` — I/O failure writing file

---

#### GET `/api/v1/learning/media/{mediaId}`

**Role:** `TEACHER` or `ADMIN`

Download a student-uploaded audio file by its `mediaId`.

**Path variable:** `mediaId` — filename token (e.g. `uuid.webm` or `uuid.wav`)

**Response `200 OK`:** binary audio stream with content type `audio/webm` or `audio/wav` based on the file extension.

**Error responses:**
- `404 NOT_FOUND` — no file with the given `mediaId` exists

---

### Internal API — prefix `/internal/learning` (no authentication required)

These endpoints are for inter-service calls only and are not protected by JWT. They are accessible to any caller that can reach the service network.

#### GET `/internal/learning/results`

Returns result summaries for a batch of tasks for a specific student. Used by the Content Service to display completion status alongside course content.

**Query parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `taskIds` | `List<Long>` | Yes | Comma-separated list of task IDs |
| `studentId` | Long | Yes | The student whose results to fetch |

**Response `200 OK`:**
```json
[
  {
    "taskId": 1,
    "status": "CHECKED",
    "aiScore": 85,
    "teacherScore": null
  },
  {
    "taskId": 2,
    "status": null,
    "aiScore": null,
    "teacherScore": null
  }
]
```

Every requested `taskId` is always present in the response; tasks without a result have `null` fields.

**Error responses:**
- `400 Bad Request` — `taskIds` list is empty

---

#### GET `/internal/learning/students/{studentId}/stats`

Returns aggregate statistics for a student across all their submitted tasks.

**Path variable:** `studentId` — Long

**Response `200 OK`:**
```json
{
  "tasksSubmitted": 17,
  "averageScore": 78.5,
  "lastActivity": "2024-01-15T10:30:00Z"
}
```

- `tasksSubmitted` — total count of task results for the student
- `averageScore` — average of `score` column; `null` if no results exist
- `lastActivity` — timestamp of the most recent submission; `null` if no results exist

---

## Services and Their Responsibilities

### `LearningService`

The central orchestrating service. Handles:

1. **`submitAnswer(taskId, studentId, request)`** — loads the task, asserts access, checks unlock gate, rejects resubmission if teacher-validated, evaluates the answer via the appropriate checker, computes effective score as `max(ai_score, teacher_score)`, upserts and saves a `TaskResult`.
2. **`getMyResults(studentId, courseId, pageable)`** — queries results for a student, optionally filtered by course.
3. **`getTaskResults(taskId, pageable)`** — returns all results for a given task (teacher view).
4. **`validateResult(resultId, request)`** — applies a teacher override score, sets status to `VALIDATED_BY_TEACHER`.

**Score rule:** effective `score = max(ai_score, teacher_score)`. Both default to `0` if absent.

---

### `AccessCheckService`

Calls `GET {CONTENT_SERVICE_URL}/internal/learning/access?studentId={id}&taskId={id}` to verify that the student belongs to a group enrolled in the course containing the task. Any 4xx response or `hasAccess: false` throws a `403 FORBIDDEN`. If the Content Service is unreachable, throws `503 SERVICE_UNAVAILABLE`. Uses a dedicated `contentRestClient` (`RestClient` bean).

---

### `UnlockEnforcementService`

Enforces three unlock modes stored in the `tasks` table (`unlock_mode` column):

| Mode | Behavior |
|---|---|
| `FREE` (or null) | No restriction; submission always allowed |
| `SEQUENTIAL` | All predecessor tasks in the same lesson (lower `order_index`) must have status `CHECKED` or `VALIDATED_BY_TEACHER` |
| `PREREQUISITE` | A specific prerequisite task (`prerequisite_task_id`) must be completed with a score at or above `required_score` |

Throws `TaskLockedException` (mapped to `403 TASK_LOCKED`) on violation.

---

### `InternalLearningService`

Serves the internal endpoints:
- **`getResultSummaries(taskIds, studentId)`** — batch-fetches results and projects them to `TaskResultSummaryDto`. Tasks with no result are included with all fields `null`.
- **`getStudentStats(studentId)`** — aggregates count, average score, and last activity timestamp.

---

### `MediaUploadService`

Handles audio file storage:
- **`upload(file)`** — validates MIME type (`audio/webm`, `audio/wav`) and size, generates a UUID-based filename, writes to `{storagePath}/{uuid}.{ext}`, returns the `mediaId`.
- **`resolve(mediaId)`** — returns the `Path` for a given `mediaId` for download purposes.

Storage path is configurable via `LEARNING_MEDIA_PATH`. Directories are created automatically on first upload.

---

### `TaskCheckerFactory`

Maps each `TaskType` to the correct `TaskChecker` implementation using an `EnumMap`:

| TaskType | Checker |
|---|---|
| `FILL_BLANKS` | `KeyBasedChecker` |
| `TRUE_FALSE` | `KeyBasedChecker` |
| `TEXT` | `AiBasedChecker` |
| `TRANSLATION` | `AiBasedChecker` |
| `VIDEO` | `AiBasedChecker` |
| `DEBATES` | `AiBasedChecker` |

---

### `KeyBasedChecker`

Evaluates `FILL_BLANKS` and `TRUE_FALSE` tasks by comparing answers to stored answer keys. All comparisons are case-insensitive with trimming.

- **FILL_BLANKS**: `answer_content` must be a JSON array of strings matching `content.answers`. Returns score `100` if all match, `0` otherwise.
- **TRUE_FALSE**: `answer_content` must be a JSON array of strings matching `content.correctOptions`. Returns score `100` if all match, `0` otherwise.

In both cases the array length must match the expected answers; a count mismatch returns `0`.

---

### `AiBasedChecker`

Evaluates `TEXT`, `TRANSLATION`, `VIDEO`, and `DEBATES` tasks by POSTing to `POST {AI_SERVICE_URL}/internal/ai/evaluate`. Builds an `AiEvaluateRequest` with task type, prompt template code, task content, student answer, and LLM options.

| TaskType | Prompt code | Temperature |
|---|---|---|
| `TEXT` | `text_evaluation` | 0.2 |
| `TRANSLATION` | `translation_evaluation` | 0.2 |
| `VIDEO` | `video_evaluation` | 0.2 |
| `DEBATES` | `debates_evaluation` | 0.8 |

All types use `max_tokens: 2048`. The full AI response is serialized as JSON and stored in `ai_breakdown`. A network error throws `503 SERVICE_UNAVAILABLE`.

---

### `TaskResultMapper`

Converts `TaskResult` JPA entity to `TaskResultResponse` DTO. Serializes the `JsonNode answerContent` field back to a JSON string.

---

## Security / Authentication

The service uses **stateless JWT bearer-token authentication**. There are no sessions or cookies.

### JWT Filter (`JwtAuthenticationFilter`)

Runs before `UsernamePasswordAuthenticationFilter`. On every request (except `/actuator/**`, `/v3/api-docs/**`, `/swagger-ui/**`):

1. Reads the `Authorization` header; skips if absent or not `Bearer`.
2. Calls `JwtService.parseAccessPrincipal()` to verify signature and parse claims.
3. On success, sets `UsernamePasswordAuthenticationToken` in the `SecurityContextHolder` with the `JwtUserPrincipal`.
4. On `JwtException`, writes `401 UNAUTHORIZED` with JSON body and halts the chain.

### JWT Token Format

Tokens are signed with HMAC-SHA (key derived from `JWT_SECRET`):

| Claim | Field | Description |
|---|---|---|
| `sub` | subject | User ID (Long, as string) |
| `typ` | type | Must be `"access"` |
| `role` | role | One of `STUDENT`, `TEACHER`, `ADMIN` |
| `email` | email | User email address |

Key derivation: if `JWT_SECRET` is valid Base64 and decodes to ≥ 32 bytes, it is used directly; otherwise the raw UTF-8 bytes are used if ≥ 32 bytes, otherwise SHA-256 is applied.

**The `JWT_SECRET` must be identical across all services** (`auth-service`, `learning-service`, etc.).

### Authorization Rules (from `SecurityConfig`)

| Path pattern | Method | Required role |
|---|---|---|
| `/actuator/**` | any | none (permit all) |
| `/internal/**` | any | none (permit all) |
| `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` | any | none (permit all) |
| `/api/v1/learning/tasks/*/submit` | POST | `STUDENT` |
| `/api/v1/learning/tasks/*/upload-audio` | POST | `STUDENT` |
| `/api/v1/learning/my-results` | GET | `STUDENT` |
| `/api/v1/learning/media/**` | GET | `TEACHER` or `ADMIN` |
| `/api/v1/learning/tasks/*/results` | GET | `TEACHER` |
| `/api/v1/learning/results/*/validate` | PATCH | `TEACHER` |
| anything else | any | authenticated |

### Roles

```
STUDENT  — can submit answers and view own results
TEACHER  — can view all task results and validate/override scores; can download student media
ADMIN    — can download student media
```

### `RequestIdFilter`

Runs at highest precedence. Reads the `X-Request-Id` header (or generates a UUID). Stores the ID in MDC and echoes it back in the response `X-Request-Id` header. All error JSON responses include a `requestId` field.

---

## Configuration: Environment Variables

All variables are read from environment (or from an `.env` file loaded externally). The `application.yml` defaults are shown in parentheses.

| Variable | Required | Default | Description |
|---|---|---|---|
| `SERVER_PORT` | No | `8083` | HTTP port the service listens on |
| `DB_URL` | No* | `jdbc:postgresql://localhost:5432/lms` | PostgreSQL JDBC URL |
| `DB_USERNAME` | No* | `postgres` | Database user |
| `DB_PASSWORD` | No* | `qwerty` | Database password |
| `JWT_SECRET` | No* | long dev default in yml | Shared JWT HMAC secret; must match auth-service |
| `AI_SERVICE_URL` | No | `http://localhost:8084` | Base URL of the AI Service |
| `CONTENT_SERVICE_URL` | No | `http://localhost:8082` | Base URL of the Content Service |
| `LEARNING_MEDIA_PATH` | No | `./data/student-media` | Filesystem path for student audio files |

\* Defaults are provided in `application.yml` for local development convenience. Always override `DB_PASSWORD` and `JWT_SECRET` in production.

### Spring multipart limits (hardcoded in `application.yml`)

| Setting | Value |
|---|---|
| `spring.servlet.multipart.max-file-size` | `20MB` |
| `spring.servlet.multipart.max-request-size` | `22MB` |

### Actuator

Only `health` and `info` endpoints are exposed at `/actuator/health` and `/actuator/info`.

### OpenAPI / Swagger

- JSON spec: `GET /v3/api-docs`
- UI: `GET /swagger-ui.html`

---

## Database

The service does **not own or run Flyway migrations**. All schema DDL is managed by `auth-service` via its Flyway migration `V2__create_content_learning_schema.sql`. The service connects with `ddl-auto: validate` — Hibernate validates that the schema matches the entity mappings at startup and will fail to start if there is a mismatch.

### Tables used

#### `tasks` (read-only)

Owned by content-service. The service maps two separate entities against this table:

**`Task` entity** — used for evaluation

| Column | Type | Description |
|---|---|---|
| `id` | bigint PK | Task identifier |
| `course_id` | bigint | Course the task belongs to |
| `type` | varchar(32) | Task type enum: `FILL_BLANKS`, `TRUE_FALSE`, `VIDEO`, `TEXT`, `TRANSLATION`, `DEBATES` |
| `content` | jsonb | Task definition: answer keys, prompt texts, etc. |
| `prompt_template_id` | bigint | Optional reference to a prompt template |
| `created_at` | timestamptz | Creation timestamp |
| `updated_at` | timestamptz | Last update timestamp |

**`TaskInfo` entity** (`@Immutable`) — used for unlock enforcement

| Column | Type | Description |
|---|---|---|
| `id` | bigint PK | Task identifier |
| `lesson_id` | bigint | Lesson the task belongs to |
| `order_index` | int | Position of the task within the lesson |
| `unlock_mode` | varchar | `FREE`, `SEQUENTIAL`, or `PREREQUISITE` |
| `prerequisite_task_id` | bigint | For `PREREQUISITE` mode: the required prior task |
| `required_score` | int | Minimum score on the prerequisite task |

Both entities map to the same `tasks` table. Learning-service never writes to this table.

---

#### `task_results` (read-write — owned by this service)

| Column | Type | Nullable | Description |
|---|---|---|---|
| `id` | bigint PK auto | No | Primary key |
| `task_id` | bigint | No | FK to `tasks.id` |
| `student_id` | bigint | No | FK to the student user |
| `answer_content` | jsonb | No | Student's submitted answer as JSON |
| `ai_feedback` | text | Yes | Natural-language feedback from AI |
| `ai_score` | int | Yes | Raw score returned by AI Service (0–100) |
| `ai_breakdown` | jsonb | Yes | Full AI response serialized as JSON |
| `teacher_score` | int | Yes | Score override set by a teacher |
| `teacher_feedback` | text | Yes | Teacher comment |
| `transcript` | text | Yes | Transcribed audio text (set externally) |
| `media_id` | varchar(255) | Yes | Filename of uploaded audio file |
| `score` | numeric(5,2) | Yes | Effective score: `max(ai_score, teacher_score)` |
| `status` | varchar(40) | No | `SUBMITTED`, `CHECKED`, `VALIDATED_BY_TEACHER` |
| `created_at` | timestamptz | No | Auto-set by Hibernate `@CreationTimestamp` |
| `updated_at` | timestamptz | No | Auto-set by Hibernate `@UpdateTimestamp` |

There is a DB-level `UNIQUE` constraint on `(student_id, task_id)` — one result row per student per task. On resubmission (allowed unless `VALIDATED_BY_TEACHER`), the existing row is updated in place.

### `ResultStatus` enum

| Value | Meaning |
|---|---|
| `SUBMITTED` | Reserved; currently not used by this service — status is set to `CHECKED` immediately after evaluation |
| `CHECKED` | AI evaluation completed; result is available |
| `VALIDATED_BY_TEACHER` | Teacher has overridden or confirmed the score; student cannot resubmit |

---

## Flyway Migrations

This service has Flyway **disabled** (`spring.flyway.enabled: false`). No migration files are present under `src/main/resources/db/migration/`. Schema creation is entirely delegated to `auth-service`.

---

## DTO Reference

### `SubmitAnswerRequest`
```json
{
  "answerContent": <JSON>,
  "mediaId": "string | null"
}
```

### `TaskResultResponse`
```json
{
  "id": 1,
  "taskId": 1,
  "studentId": 10,
  "answerContent": "<JSON string>",
  "aiFeedback": "string",
  "aiScore": 85,
  "aiBreakdown": "<JSON string>",
  "teacherScore": null,
  "teacherFeedback": null,
  "score": 85.00,
  "transcript": null,
  "mediaId": null,
  "status": "CHECKED",
  "createdAt": "ISO-8601",
  "updatedAt": "ISO-8601"
}
```

### `ValidateResultRequest`
```json
{
  "score": 95,
  "comment": "string | null"
}
```

### `PagedResponse<T>`
```json
{
  "content": [ ... ],
  "totalElements": 42,
  "totalPages": 3
}
```

### `TaskResultSummaryDto` (internal)
```json
{
  "taskId": 1,
  "status": "CHECKED | null",
  "aiScore": 85,
  "teacherScore": null
}
```

### `StudentStatsDto` (internal)
```json
{
  "tasksSubmitted": 17,
  "averageScore": 78.5,
  "lastActivity": "ISO-8601"
}
```

### `MediaUploadResponse`
```json
{
  "mediaId": "uuid.webm"
}
```

### `ApiError` (all error responses)
```json
{
  "code": "NOT_FOUND",
  "message": "Task not found: 99",
  "requestId": "uuid"
}
```

### `AiEvaluateRequest` (outbound to AI Service)
```json
{
  "task_type": "TEXT",
  "prompt_template_code": "text_evaluation",
  "task_content": { ... },
  "student_answer": "...",
  "options": {
    "temperature": 0.2,
    "max_tokens": 2048
  }
}
```

### `AiEvaluateResponse` (inbound from AI Service)
```json
{
  "score": 85,
  "feedback": "...",
  "verdict": "Accepted"
}
```

---

## Exception Handling

`GlobalExceptionHandler` (`@RestControllerAdvice`) maps exceptions to HTTP responses:

| Exception | HTTP status | Code |
|---|---|---|
| `TaskLockedException` | `403` | `TASK_LOCKED` |
| `ApiBusinessException` | dynamic (from exception) | dynamic (from exception) |
| `MethodArgumentNotValidException` | `400` | `VALIDATION_ERROR` |
| `ConstraintViolationException` | `400` | `VALIDATION_ERROR` |
| `Exception` (fallback) | `500` | `INTERNAL_ERROR` |

`ApiBusinessException` factory methods and their codes:

| Method | HTTP | Code |
|---|---|---|
| `notFound(entity, id)` | 404 | `NOT_FOUND` |
| `forbidden()` | 403 | `FORBIDDEN` |
| `conflict(msg)` | 409 | `CONFLICT` |
| `badRequest(msg)` | 400 | `VALIDATION_ERROR` |
| `serviceUnavailable(msg)` | 503 | `SERVICE_UNAVAILABLE` |
| `validationError(msg)` | 422 | `VALIDATION_ERROR` |

---

## Tests

Run all tests:
```bash
mvn test
```

### `KeyBasedCheckerTest`

Unit test for `KeyBasedChecker`. No Spring context — plain unit test.

| Test | What it covers |
|---|---|
| `fillBlanks_allCorrect_returns100` | All blank answers match → score 100 |
| `fillBlanks_wrongAnswer_returns0` | One answer wrong → score 0 |
| `fillBlanks_caseInsensitive_returns100` | Case-insensitive comparison |
| `fillBlanks_wrongCount_returns0` | Array length mismatch → score 0 |
| `trueFalse_allCorrect_returns100` | All true/false answers match → score 100 |
| `trueFalse_wrongAnswer_returns0` | One true/false answer wrong → score 0 |
| `invalidJson_throwsBadRequest` | Non-JSON `answerContent` throws `ApiBusinessException` with `"Invalid answer format"` |

---

### `AiBasedCheckerTest`

Unit test for `AiBasedChecker` with a mocked `RestClient` chain.

| Test | What it covers |
|---|---|
| `check_textTask_returnsEvaluationResult` | Successful AI call for `TEXT` task returns correct score and feedback |
| `check_aiServiceDown_throwsServiceUnavailable` | `RestClientException` from `retrieve()` is converted to `503 SERVICE_UNAVAILABLE` `ApiBusinessException` |

---

### `LearningServiceTest`

Unit test for `LearningService` with all dependencies mocked via Mockito (`@ExtendWith(MockitoExtension.class)`).

| Test | What it covers |
|---|---|
| `submitAnswer_newResult_savesAndReturns` | Happy path: task found, access granted, no prior result, evaluation returns 100, result saved and returned |
| `submitAnswer_taskNotFound_throws404` | Missing task ID → `ApiBusinessException` with `"not found"` |
| `submitAnswer_noAccess_throws403` | `AccessCheckService` throws forbidden → propagated |
| `submitAnswer_taskLocked_throws403` | `UnlockEnforcementService` throws `TaskLockedException` → propagated |
| `submitAnswer_alreadyValidated_throws409` | Existing result with `VALIDATED_BY_TEACHER` status → `409 CONFLICT` |
| `validateResult_updatesScoreAndStatus` | Teacher override sets score to 95, status to `VALIDATED_BY_TEACHER` |

---

### `LearningControllerTest`

`@WebMvcTest` slice test for `LearningController`, importing `SecurityConfig`, `JwtAuthenticationFilter`, and `RequestIdFilter`. Uses `SecurityMockMvcRequestPostProcessors.authentication()` to inject pre-built principals.

| Test | What it covers |
|---|---|
| `submit_asStudent_returns200` | STUDENT can POST to `/submit`; response has `status: CHECKED` |
| `submit_asTeacher_returns403` | TEACHER cannot POST to `/submit` |
| `myResults_asStudent_returns200` | STUDENT can GET `/my-results`; response has `totalElements` |
| `taskResults_asTeacher_returns200` | TEACHER can GET `/tasks/{id}/results` |
| `taskResults_asStudent_returns403` | STUDENT cannot GET `/tasks/{id}/results` |
| `validate_asTeacher_returns200` | TEACHER can PATCH `/results/{id}/validate`; response has `status: VALIDATED_BY_TEACHER` |
| `validate_asStudent_returns403` | STUDENT cannot PATCH `/results/{id}/validate` |

---

## How to Run Locally

**Prerequisites:**
- Java 17+
- Maven 3.8+
- PostgreSQL running with the LMS schema (applied by `auth-service` Flyway migrations)

```bash
cd app/learning-service
cp .env.example .env
# Edit .env and set DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET
# Ensure AI_SERVICE_URL and CONTENT_SERVICE_URL point to running instances

# Export variables (Linux/macOS)
export $(grep -v '^#' .env | xargs)

# Or on Windows PowerShell
Get-Content .env | Where-Object { $_ -notmatch '^#' } | ForEach-Object { $name,$value = $_ -split '=',2; [System.Environment]::SetEnvironmentVariable($name, $value) }

mvn spring-boot:run
```

- **API:** `http://localhost:8083/api/v1/learning/`
- **Swagger UI:** `http://localhost:8083/swagger-ui.html`
- **OpenAPI JSON:** `http://localhost:8083/v3/api-docs`
- **Health:** `http://localhost:8083/actuator/health`

---

## Docker Build

The service uses a two-stage Dockerfile:

1. **Build stage** (`eclipse-temurin:17-jdk-alpine`): installs Maven, copies `pom.xml` and `src/`, runs `mvn -q -B package -DskipTests`, producing the fat JAR at `target/learning-service-*.jar`.
2. **Runtime stage** (`eclipse-temurin:17-jre-alpine`): creates a non-root `spring` user, copies the JAR, exposes port `8083`, runs `java -jar /app/app.jar`.

**Build:**
```bash
cd app/learning-service
docker build -t lms-learning-service:latest .
```

**Run:**
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

Or with an env file (do not commit real secrets):
```bash
docker run --rm -p 8083:8083 --env-file .env lms-learning-service:latest
```

**Notes:**
- On Linux, `host.docker.internal` may not resolve; use the host gateway IP or put all services on the same Docker network and use service name URLs (e.g., `http://content-service:8082`).
- Media files written inside the container to `./data/student-media` will be lost when the container stops. Mount a volume to persist them: `-v /host/path:/app/data/student-media`.
- The `LEARNING_MEDIA_PATH` environment variable controls the storage path inside the container.
