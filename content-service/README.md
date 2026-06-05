# content-service

The content-service is the central content management microservice of the LMS platform. It owns and manages the full content hierarchy — courses, topics, lessons, lesson blocks, and tasks — as well as student groups, group-to-course assignments, and student membership. It enforces role-based access control (STUDENT, TEACHER, ADMIN), coordinates with the AI service to generate lesson/task/topic content on demand, supports course import and export as ZIP archives, and exposes an internal endpoint used by the learning-service to verify student access before submission.

---

## Tech Stack

| Component | Library / Version |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2.5 |
| Persistence | Spring Data JPA + Hibernate (PostgreSQL dialect) |
| Database | PostgreSQL (driver: `org.postgresql:postgresql`) |
| Security | Spring Security (stateless JWT, HMAC-SHA256 via `io.jsonwebtoken:jjwt` 0.12.5) |
| Validation | `spring-boot-starter-validation` (Jakarta Bean Validation) |
| API docs | springdoc-openapi 2.5.0 — Swagger UI at `/swagger-ui.html` |
| Observability | Spring Actuator (`health`, `info` exposed) |
| Testing | JUnit 5 + Mockito + AssertJ (`spring-boot-starter-test`, `spring-security-test`) |
| Build | Maven, `spring-boot-maven-plugin` |

---

## Security / Authentication

All public endpoints (everything except `/actuator/**`, `/v3/api-docs/**`, `/swagger-ui/**`, and `/internal/learning/**`) require a valid JWT bearer token.

**Flow:**

1. `RequestIdFilter` (highest precedence) reads or generates a `X-Request-Id` header and stores it in MDC.
2. `JwtAuthenticationFilter` intercepts every request, reads `Authorization: Bearer <token>`, parses the JWT with `JwtService`, and populates the Spring Security `SecurityContextHolder` with a `JwtUserPrincipal`.
3. If the token is absent the request proceeds as anonymous (unauthenticated). Spring Security then rejects the request with a 401 JSON error if the endpoint requires authentication.
4. If the token is present but invalid/expired, a 401 is returned immediately.

**JWT structure:**

| Claim | Description |
|---|---|
| `sub` | User ID (Long, as string) |
| `typ` | Must be `"access"` |
| `role` | `STUDENT`, `TEACHER`, or `ADMIN` |
| `email` | User e-mail |

**Key resolution:** `JwtService` accepts the secret as a Base64-encoded key (≥ 32 decoded bytes) or as a UTF-8 passphrase (hashed with SHA-256 if shorter than 32 bytes).

**The `/internal/learning/**` path is permitted without a token** — it is intended for service-to-service communication from the learning-service only.

**Roles and access policy:**

| Role | Behaviour |
|---|---|
| `ADMIN` | Full access to all resources |
| `TEACHER` | Full access to resources they own (authorId / teacherId = their userId) |
| `STUDENT` | Read-only access to courses/tasks/lessons accessible through group membership |

---

## REST Endpoints

All public endpoints are routed through the API Gateway on port 8080. Direct port 8082 is used for service-to-service and local development. **Two authentication patterns coexist:**
- Endpoints backed by `JwtUserPrincipal` use the `Authorization: Bearer` header (parsed by the filter).
- Endpoints on `TopicController`, `LessonController`, `BlockController`, and `AiController` accept the identity via explicit `X-User-Id` / `X-User-Role` request headers (injected by the gateway after verifying the JWT).

### Courses — `GET/POST /api/v1/content/courses`

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/v1/content/courses` | TEACHER, ADMIN, STUDENT | List courses (paginated, sorted by `createdAt` desc). ADMIN sees all; TEACHER sees own; STUDENT sees courses of their groups. Query params: standard Spring `Pageable` (`page`, `size`, `sort`). |
| POST | `/api/v1/content/courses` | TEACHER, ADMIN | Create a course. |
| GET | `/api/v1/content/courses/{id}` | TEACHER (owner), ADMIN, STUDENT (via group) | Get a single course. |
| PUT | `/api/v1/content/courses/{id}` | TEACHER (owner), ADMIN | Update course title/description. |
| DELETE | `/api/v1/content/courses/{id}` | TEACHER (owner), ADMIN | Delete course. Returns 204. |
| GET | `/api/v1/content/courses/{id}/tree` | TEACHER (owner), ADMIN | Get full course tree (topics → lessons with block/task counts). |
| GET | `/api/v1/content/courses/{id}/stats` | TEACHER (owner), ADMIN | Get course statistics (topic/lesson/task counts by status, students assigned). |
| GET | `/api/v1/content/courses/{id}/export` | TEACHER (owner), ADMIN | Export course as ZIP (Content-Disposition: attachment). |
| GET | `/api/v1/content/courses/{id}/export.json` | TEACHER (owner), ADMIN | Export course as JSON body. |
| POST | `/api/v1/content/courses/import` | TEACHER, ADMIN | Import course from ZIP (`multipart/form-data`, field `file`). Optional `targetCourseId` param to merge into an existing course. |

**Create/Update course request body:**
```json
{ "title": "string (required, max 500)", "description": "string (optional)" }
```

**Course response body:**
```json
{
  "id": 1,
  "title": "English B2",
  "description": "...",
  "authorId": 10,
  "createdAt": "2024-01-01T00:00:00Z",
  "updatedAt": "2024-01-01T00:00:00Z"
}
```

**Course tree response** (`GET /courses/{id}/tree`):
```json
{
  "id": 1, "title": "...", "description": "...", "level": "B2",
  "publishMode": "AUTO", "accessStatus": "OPEN",
  "topics": [
    {
      "id": 10, "title": "Unit 1", "description": "...", "orderIndex": 0,
      "lessons": [
        { "id": 100, "title": "Lesson 1", "status": "PUBLISHED",
          "orderIndex": 0, "unlockMode": "FREE", "visible": true,
          "blocksCount": 3, "tasksCount": 2 }
      ]
    }
  ]
}
```

**Course stats response** (`GET /courses/{id}/stats`):
```json
{
  "topicsCount": 3, "lessonsCount": 12, "tasksCount": 48,
  "publishedLessons": 10, "draftLessons": 1, "aiGeneratedLessons": 1,
  "studentsAssigned": 25
}
```

**Import result response** (`POST /courses/import`):
```json
{ "courseId": 42, "topicsImported": 3, "lessonsImported": 12, "tasksImported": 48, "warnings": [] }
```

---

### Topics — `/api/v1/content`

Headers required: `X-User-Id: <Long>`, `X-User-Role: <STUDENT|TEACHER|ADMIN>`.

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/content/courses/{courseId}/topics` | List topics for a course (ordered by `orderIndex`). |
| POST | `/api/v1/content/courses/{courseId}/topics` | Create topic in a course. |
| GET | `/api/v1/content/topics/{topicId}` | Get topic (without lessons). |
| GET | `/api/v1/content/topics/{topicId}/full` | Get topic with lesson summaries. |
| PATCH | `/api/v1/content/topics/{topicId}` | Update topic title/description/orderIndex. |
| DELETE | `/api/v1/content/topics/{topicId}` | Delete topic. Returns 204. |
| PATCH | `/api/v1/content/courses/{courseId}/topics/reorder` | Reorder all topics in a course. |

**Topic request body (create/update):**
```json
{ "title": "string (required, max 500)", "description": "string", "orderIndex": 0 }
```

**Topic response:**
```json
{
  "id": 10, "courseId": 1, "title": "Unit 1", "description": "...",
  "orderIndex": 0, "globalOrder": 0, "createdAt": "..."
}
```

**Reorder request body:**
```json
{ "order": [10, 12, 11] }
```

---

### Lessons — `/api/v1/content`

Headers required: `X-User-Id`, `X-User-Role`.

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/content/courses/{courseId}/lessons` | List all lessons for a course. |
| POST | `/api/v1/content/courses/{courseId}/lessons` | Create a lesson attached to a course (optionally to a topic via `topicId` in body). |
| POST | `/api/v1/content/topics/{topicId}/lessons` | Create a lesson inside a specific topic. |
| GET | `/api/v1/content/lessons/{id}` | Get lesson with full content (blocks + tasks). |
| PATCH | `/api/v1/content/lessons/{id}` | Update lesson metadata (title, topicId, unlockMode, visible, publishMode). |
| DELETE | `/api/v1/content/lessons/{id}` | Delete lesson. Returns 204. |
| POST | `/api/v1/content/lessons/{id}/publish` | Publish a lesson. Body: `{ "confirmed": true/false }`. If `publishMode` is `REVIEW` and status is `AI_GENERATED`, `confirmed: true` is required. |
| PATCH | `/api/v1/content/lessons/{id}/tasks/reorder` | Reorder tasks within a lesson. Body: `{ "order": [taskId, ...] }`. |
| POST | `/api/v1/content/lessons/{id}/access` | Grant a student individual lesson access. Body: `{ "studentId": 42 }`. |
| DELETE | `/api/v1/content/lessons/{id}/access/{studentId}` | Revoke student access. Returns 204. |
| GET | `/api/v1/content/lessons/{id}/access` | List all students who have individual access to the lesson. |
| GET | `/api/v1/content/lessons/{id}/available-tasks` | Compute task availability for a student (unlock rules). Query param: `studentId` (required for TEACHER/ADMIN, auto-resolved for STUDENT). |
| GET | `/api/v1/content/lessons/{lessonId}/tasks` | List tasks in lesson ordered by `orderIndex`. |
| POST | `/api/v1/content/lessons/{lessonId}/tasks` | Create a task within a lesson. |

**Lesson request body:**
```json
{
  "title": "Lesson 1 (required, max 500)",
  "topicId": 10,
  "publishMode": "AUTO",
  "unlockMode": "FREE",
  "visible": true
}
```

**Lesson response:**
```json
{
  "id": 100, "courseId": 1, "topicId": 10,
  "orderIndex": 0, "globalOrder": 0,
  "title": "Lesson 1", "status": "DRAFT",
  "publishMode": "AUTO", "unlockMode": "FREE",
  "visible": true, "blocksCount": 3, "tasksCount": 2,
  "createdAt": "...", "publishedAt": null
}
```

**Lesson with content response** (`GET /lessons/{id}`): same as above plus:
```json
{
  "blocks": [ { "id": 1, "lessonId": 100, "orderIndex": 0, "type": "TEXT",
                "contentJson": "{...}", "aiGenerated": false, "createdAt": "..." } ],
  "tasks": [ { "id": 200, "courseId": 1, "lessonId": 100, "type": "FILL_BLANKS",
               "title": "...", "content": {}, "status": "DRAFT", "orderIndex": 0,
               "unlockMode": "FREE", "prerequisiteTaskId": null, "requiredScore": null,
               "aiGenerated": false, "promptTemplateId": null,
               "createdAt": "...", "updatedAt": "..." } ]
}
```

**Task availability result** (`GET /lessons/{id}/available-tasks`):
```json
[
  { "taskId": 200, "available": true, "reason": null },
  { "taskId": 201, "available": false, "reason": "score_below_70" }
]
```

**Lesson access entry response** (`GET /lessons/{id}/access`):
```json
[ { "studentId": 42, "grantedAt": "..." } ]
```

---

### Lesson Blocks — `/api/v1/content`

Headers required: `X-User-Id`, `X-User-Role`.

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/content/lessons/{lessonId}/blocks` | List blocks in a lesson ordered by `orderIndex`. |
| POST | `/api/v1/content/lessons/{lessonId}/blocks` | Create a block in a lesson. Content is validated per block type. |
| PATCH | `/api/v1/content/blocks/{blockId}` | Update a block. |
| DELETE | `/api/v1/content/blocks/{blockId}` | Delete a block. Returns 204. |

**Block request body:**
```json
{
  "type": "TEXT",
  "contentJson": "{...}",
  "aiGenerated": false,
  "orderIndex": 0
}
```

**Block types and required `contentJson` fields:**

| BlockType | Required fields in `contentJson` |
|---|---|
| `TEXT` | `format` (`"markdown"` or `"html"`), `text` |
| `AUDIO` | `tts_script`, `voice` |
| `IMAGE` | `caption`, `url` |
| `VIDEO` | `url`, `caption` |

**Block response:**
```json
{ "id": 1, "lessonId": 100, "orderIndex": 0, "type": "TEXT",
  "contentJson": "{...}", "aiGenerated": false, "createdAt": "..." }
```

---

### Tasks — `/api/v1/content/tasks`

Auth: JWT bearer token, `@AuthenticationPrincipal`.

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/v1/content/tasks` | TEACHER, ADMIN | List all tasks (paginated). ADMIN sees all; TEACHER sees tasks from their own courses. STUDENT: 403. |
| GET | `/api/v1/content/tasks/{id}` | TEACHER, ADMIN, STUDENT (via group) | Get task by ID. |
| POST | `/api/v1/content/tasks` | TEACHER (owner), ADMIN | Create a standalone task (not linked to a lesson). Content is validated per task type. |
| PUT | `/api/v1/content/tasks/{id}` | TEACHER (owner), ADMIN | Update task type, content, promptTemplateId, title, unlockMode, prerequisiteTaskId, requiredScore. |
| DELETE | `/api/v1/content/tasks/{id}` | TEACHER (owner), ADMIN | Delete task. Returns 204. |

**Create task request body** (standalone):
```json
{
  "courseId": 1,
  "type": "FILL_BLANKS",
  "content": { "text": "Complete ___ sentence.", "answers": ["the"] },
  "promptTemplateId": null
}
```

**Create task in lesson request body** (`POST /lessons/{lessonId}/tasks`):
```json
{
  "type": "FILL_BLANKS",
  "content": { "text": "Complete ___ sentence.", "answers": ["the"] },
  "title": "Optional title",
  "unlockMode": "FREE",
  "prerequisiteTaskId": null,
  "requiredScore": null,
  "promptTemplateId": null
}
```

**Task types and required `content` fields:**

| TaskType | Required fields |
|---|---|
| `FILL_BLANKS` | `text` (string), `answers` (non-empty array) |
| `TRUE_FALSE` | `questions` (non-empty array), `correctOptions` (non-empty array) |
| `VIDEO` | `videoUrl` (string), `transcript` (string), `questions` (non-empty array) |
| `TEXT` | `sourceText` (string), `questions` (non-empty array), `level` (string) |
| `TRANSLATION` | `sourceText` (string), `instructions` (string) |
| `DEBATES` | `topic` (string), `botRole` (string) |
| `LISTENING` | `audioUrl` (string), `questions` (non-empty array) |
| `SPEAKING` | `prompt` (string) |
| `READING_COMPREHENSION` | `sourceText` (string), `questions` (non-empty array) |
| `IMAGE_DESCRIPTION` | `imageUrl` (string), `prompt` (string) |

**Task unlock modes:**

| unlockMode | Behaviour |
|---|---|
| `FREE` (default) | Always available |
| `SEQUENTIAL` | All tasks with lower `orderIndex` in the same lesson must be `CHECKED` or `VALIDATED_BY_TEACHER` |
| `PREREQUISITE` | A specific prerequisite task must be completed with a score ≥ `requiredScore` (0–100) |

**Task response:**
```json
{
  "id": 200, "courseId": 1, "lessonId": 100,
  "type": "FILL_BLANKS", "title": "...",
  "content": { "text": "Complete ___ sentence.", "answers": ["the"] },
  "status": "DRAFT", "orderIndex": 0,
  "unlockMode": "FREE", "prerequisiteTaskId": null, "requiredScore": null,
  "aiGenerated": false, "promptTemplateId": null,
  "createdAt": "...", "updatedAt": "..."
}
```

---

### Groups — `/api/v1/content/groups`

Auth: JWT bearer token.

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/v1/content/groups` | TEACHER, ADMIN | List groups (paginated). TEACHER sees own groups; ADMIN sees all. STUDENT: 403. |
| GET | `/api/v1/content/groups/{id}` | TEACHER (owner), ADMIN | Get group by ID. |
| POST | `/api/v1/content/groups` | TEACHER, ADMIN | Create group. `teacherId` is set from the JWT principal. |
| PUT | `/api/v1/content/groups/{id}` | TEACHER (owner), ADMIN | Rename group. |
| DELETE | `/api/v1/content/groups/{id}` | TEACHER (owner), ADMIN | Delete group. Returns 204. |
| POST | `/api/v1/content/groups/{groupId}/students` | TEACHER (owner), ADMIN | Add a single student to a group. Body: `{ "userId": 42 }`. Returns 409 if already member. |
| DELETE | `/api/v1/content/groups/{groupId}/students/{userId}` | TEACHER (owner), ADMIN | Remove student from group. Returns 204. |
| POST | `/api/v1/content/groups/{groupId}/students/bulk` | TEACHER (owner), ADMIN | Bulk-add up to 200 students. Deduplicates input; skips non-student users and existing members. |
| POST | `/api/v1/content/groups/{groupId}/courses` | TEACHER (owner), ADMIN | Assign a course to the group. Body: `{ "courseId": 1 }`. Returns 409 if already assigned. |
| DELETE | `/api/v1/content/groups/{groupId}/courses/{courseId}` | TEACHER (owner), ADMIN | Remove course from group. Returns 204. |

**Create/update group request body:**
```json
{ "name": "Group A (required, max 255)" }
```

**Group response:**
```json
{ "id": 5, "name": "Group A", "teacherId": 10, "createdAt": "..." }
```

**Bulk add students request:**
```json
{ "studentIds": [42, 43, 44] }
```

**Bulk add students response:**
```json
{ "added": [43, 44], "alreadyInGroup": [42], "notFound": [99] }
```

---

### Students — `/api/v1/content/students`

Auth: JWT bearer token. STUDENT callers always get 403.

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/v1/content/students` | TEACHER, ADMIN | List active students (paginated). Query params: `q` (search by name/email), `groupId` (filter to group), `notInGroup=true&groupId=X` (exclude group members). TEACHER only sees students in their groups. |
| GET | `/api/v1/content/students/{id}` | TEACHER (if student in their group), ADMIN | Get student card with learning statistics (fetched from learning-service). |

**Student summary response:**
```json
{
  "id": 42, "email": "student@example.com",
  "firstName": "Jane", "lastName": "Doe", "displayName": "Jane Doe",
  "active": true,
  "groups": [ { "id": 5, "name": "Group A" } ],
  "createdAt": "..."
}
```

**Student card response** (extends summary):
```json
{
  "id": 42, "email": "...", "firstName": "Jane", "lastName": "Doe",
  "displayName": "Jane Doe", "active": true, "groups": [...], "createdAt": "...",
  "stats": { "tasksSubmitted": 15, "averageScore": 72.5, "lastActivity": "..." }
}
```

---

### AI Generation — `/api/v1/content/ai`

Headers required: `X-User-Id`, `X-User-Role`. TEACHER and ADMIN only (STUDENT: 403).

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/content/ai/generate-lesson` | Generate a full lesson (blocks + tasks) via the AI service. Persists and returns the lesson. |
| POST | `/api/v1/content/ai/generate-task` | Generate a single task for a lesson via the AI service. |
| POST | `/api/v1/content/ai/generate-topic` | Generate a topic with multiple lessons via the AI service. |
| POST | `/api/v1/content/lessons/{id}/regenerate` | Regenerate an existing lesson (replace non-preserved blocks and tasks). |

**Generate lesson request body:**
```json
{
  "courseId": 1,
  "topicId": 10,
  "topic": "Greetings",
  "level": "A1",
  "length": "short",
  "taskTypes": ["FILL_BLANKS", "TRUE_FALSE"],
  "includeTheory": true,
  "instructions": "Focus on formal greetings"
}
```

**Generate task request body:**
```json
{ "lessonId": 100, "type": "SPEAKING", "context": "...", "level": "B2" }
```

**Generate topic request body:**
```json
{ "courseId": 1, "topicTitle": "Travel", "description": "...", "level": "B1", "lessonCount": 3 }
```

**Generate topic response:**
```json
{ "topicId": 10, "topicTitle": "Travel", "lessonsCreated": 3, "totalTasks": 12 }
```

**Regenerate lesson request body:**
```json
{
  "hint": "Make it harder",
  "preserveIds": [101, 102],
  "taskTypes": ["DEBATES", "SPEAKING"]
}
```

---

### Internal — `/internal/learning`

Not exposed through the API Gateway. Used by learning-service for pre-submission access checks.

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/internal/learning/access` | None (service-to-service) | Check if a student has access to a task via group membership. Query params: `studentId`, `taskId`. |

**Response:**
```json
{ "hasAccess": true }
```

---

## API Conventions

### Paginated Responses

All list endpoints that accept Spring `Pageable` parameters (`page`, `size`, `sort`) return a `PagedResponse<T>`:

```json
{
  "content": [ /* array of items */ ],
  "totalElements": 100,
  "totalPages": 10
}
```

Applies to: `GET /courses`, `GET /tasks`, `GET /groups`, `GET /students`.

### Error Responses

All errors follow a single `ApiError` envelope:

```json
{
  "code": "NOT_FOUND",
  "message": "Course not found: 42",
  "requestId": "a1b2c3d4"
}
```

| HTTP status | `code` value | Trigger |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Bean-validation failure or bad request body |
| 403 | `FORBIDDEN` | Caller lacks permission for the resource |
| 404 | `NOT_FOUND` | Entity does not exist |
| 409 | `CONFLICT` | Duplicate membership / already assigned |
| 503 | `SERVICE_UNAVAILABLE` | Downstream service (AI / learning) unreachable |
| 500 | `INTERNAL_ERROR` | Unexpected server error |

`requestId` echoes the `X-Request-Id` header so errors can be correlated in logs.

---

## Services and Their Responsibilities

| Service | Responsibilities |
|---|---|
| `CourseService` | CRUD for courses; role-filtered listing; write access restricted to owner teacher or admin; student access via group membership check. |
| `CourseTreeService` | Builds the full hierarchical tree (course → topics → lessons with block/task counts) using a native SQL query. |
| `CourseStatsService` | Aggregates per-course statistics (topic/lesson/task counts by status, students assigned) using native SQL. |
| `TopicService` | CRUD and reordering of topics within a course; delegates `createInTopic` to `LessonService`; validates reorder list completeness. |
| `LessonService` | CRUD for lessons; publish lifecycle (DRAFT → PUBLISHED with REVIEW gate for AI-generated content); task reordering within a lesson; individual lesson-access grant/revoke for students. |
| `BlockService` | CRUD for lesson blocks; delegates content validation to `BlockContentValidator`; auto-increments `orderIndex`. |
| `TaskService` | CRUD for tasks (standalone and in-lesson); JSONB content validation via `ContentJsonValidator`; unlock-mode validation via `TaskUnlockValidator`; role-filtered listing. |
| `GroupService` | CRUD for student groups; student add/remove (single and bulk, max 200 per request); course assignment to groups; bulk-add categorises results as `added`, `alreadyInGroup`, or `notFound`. |
| `StudentService` | Read-only view of students (queries shared `users` table via native SQL); paginated and searchable list with group filter; fetches learning statistics from learning-service for student cards. |
| `AiGenerationService` | Orchestrates AI content generation (lesson, task, topic, regenerate-lesson); calls `AiGenerationClient`; persists returned structures (blocks and tasks) as JPA entities with `aiGenerated = true` and status `AI_GENERATED`. |
| `LearningAccessService` | Internal service: verifies student has access to a task (task exists + student is in a group with the task's course assigned). |
| `AvailableTasksService` | Computes per-task availability for a student using unlock-mode rules (`FREE`, `SEQUENTIAL`, `PREREQUISITE`); calls `LearningResultsClient` only when non-FREE tasks exist. |
| `CourseExportService` | Serialises a course (topics → lessons → blocks + tasks) to `CourseExportDto`; packages it as a ZIP with `manifest.json` (SHA-256 checksum) and `course.json`. |
| `CourseImportService` | Validates ZIP security; parses `manifest.json` and `course.json`; verifies checksum; either creates a new course (`copyCourse`) or merges into an existing one (`mergeCourse`, matched by topic/lesson title). Prerequisite task IDs are remapped after import. |
| `ContentJsonValidator` | Validates `content` JSONB map for all 10 task types; throws 400 if required fields are missing or empty. |
| `BlockContentValidator` | Validates `contentJson` string for all 4 block types; parses as JSON then checks required string fields. |
| `TaskUnlockValidator` | Validates unlock-mode configuration when creating or updating a task: `SEQUENTIAL` must not have `prerequisiteTaskId`/`requiredScore`; `PREREQUISITE` must reference a task in the same lesson with no circular dependency. |
| `CourseAccessChecker` | Shared utility: verifies that a user (by header-supplied userId/role) has access to a course (admin bypass, author match, or `course_teachers` membership). |
| `ContentMapper` | Simple entity-to-DTO mapper for `Course`, `Task`, and `Group`. |

---

## External Service Clients

| Client | Target service | How configured |
|---|---|---|
| `LearningResultsClient` | learning-service | `GET ${LEARNING_SERVICE_URL}/internal/learning/results?taskIds=...&studentId=...` — returns `List<TaskResultSummary>` (taskId, status, aiScore, teacherScore). |
| `LearningStatsClient` | learning-service | `GET ${LEARNING_SERVICE_URL}/internal/learning/students/{id}/stats` — returns `StudentStatsDto` (tasksSubmitted, averageScore, lastActivity). |
| `AiGenerationClient` | ai-service | POSTs to `${AI_SERVICE_URL}/internal/ai/generate-lesson`, `/generate-task`, `/generate-topic`, `/regenerate-lesson`. Uses a dedicated `aiRestTemplate` bean. |

All clients throw `ApiBusinessException("SERVICE_UNAVAILABLE", 503, ...)` on HTTP errors or empty responses.

---

## Configuration: Environment Variables

All variables correspond to placeholders in `application.yml`. Defaults are listed below.

| Variable | Default | Description |
|---|---|---|
| `SERVER_PORT` | `8082` | HTTP port the service listens on. |
| `DB_URL` | `jdbc:postgresql://localhost:5432/lms` | JDBC URL for PostgreSQL. |
| `DB_USERNAME` | `postgres` | PostgreSQL username. |
| `DB_PASSWORD` | `qwerty` | PostgreSQL password. |
| `JWT_SECRET` | `LmAxBynYUYDh9uLxzGYCHCGSIVceE2E87aLSRtVVgAGxLlHtbz6Hn3TMP4DwYfPyjEfwUlKopDnLKnB4UpTexV` | HMAC secret shared with auth-service and gateway-service. **Must match across all services.** |
| `LEARNING_SERVICE_URL` | `http://localhost:8083` | Base URL of the learning-service (no trailing slash). |
| `AI_SERVICE_URL` | `http://localhost:8084` | Base URL of the ai-service (no trailing slash). |
| `IMPORT_MAX_SIZE_MB` | `50` | Maximum ZIP file size for course import (MB). |
| `IMPORT_MAX_UNZIPPED_MB` | `200` | Maximum total unzipped size for course import (MB). |
| `IMPORT_MAX_FILES` | `5000` | Maximum number of files inside the ZIP. |

Additional Spring defaults (not typically overridden):
- `spring.jpa.hibernate.ddl-auto=validate` — schema validated at startup, not managed.
- `spring.flyway.enabled=false` — Flyway is disabled; schema is managed by auth-service.
- `spring.servlet.multipart.max-file-size=100MB` / `max-request-size=100MB` — for course ZIP import.

---

## Database: Entities and Tables

The service connects to the shared LMS PostgreSQL database. The schema is owned by the auth-service (Flyway migrations). This service uses `ddl-auto: validate`.

### `courses`

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT PK | Auto-generated. |
| `title` | VARCHAR(500) NOT NULL | Course title. |
| `description` | TEXT NOT NULL (default `''`) | Course description. |
| `author_id` | BIGINT NOT NULL | ID of the teacher who created the course. |
| `publish_mode` | VARCHAR(16) NOT NULL (default `AUTO`) | `AUTO` or `REVIEW`. |
| `level` | VARCHAR(8) NOT NULL (default `A1`) | Language level (e.g. A1, B2). |
| `access_status` | VARCHAR(16) NOT NULL (default `OPEN`) | `OPEN` or other values. |
| `created_at` | TIMESTAMPTZ NOT NULL | Set by `@CreationTimestamp`. |
| `updated_at` | TIMESTAMPTZ NOT NULL | Set by `@UpdateTimestamp`. |

### `topics`

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT PK | Auto-generated. |
| `course_id` | BIGINT NOT NULL | FK to `courses`. |
| `title` | VARCHAR(500) NOT NULL | Topic title. |
| `description` | TEXT | Optional description. |
| `order_index` | INT NOT NULL | Position within the course. |
| `global_order` | INT | Optional global ordering across all topics. |
| `created_at` | TIMESTAMPTZ NOT NULL | Set on construction. |

### `lessons`

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT PK | Auto-generated. |
| `course_id` | BIGINT NOT NULL | FK to `courses`. |
| `topic_id` | BIGINT | FK to `topics` (nullable — lessons can exist without a topic). |
| `order_index` | INT NOT NULL | Position within the topic or course. |
| `global_order` | INT | Optional global ordering across all lessons. |
| `title` | VARCHAR(500) NOT NULL | Lesson title. |
| `status` | VARCHAR(32) NOT NULL (default `DRAFT`) | `DRAFT`, `AI_GENERATED`, or `PUBLISHED`. |
| `publish_mode` | VARCHAR(16) | `AUTO` or `REVIEW`. Inherits from course if null. |
| `unlock_mode` | VARCHAR(16) NOT NULL (default `FREE`) | `FREE` or other values. |
| `visible` | BOOLEAN NOT NULL (default `true`) | Whether lesson is visible to students. |
| `generation_metadata` | JSONB | AI generation metadata (opaque). |
| `created_at` | TIMESTAMPTZ NOT NULL | Set on construction. |
| `published_at` | TIMESTAMPTZ | Set when lesson is published. |

### `lesson_blocks`

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT PK | Auto-generated. |
| `lesson_id` | BIGINT NOT NULL | FK to `lessons`. |
| `order_index` | INT NOT NULL | Position within the lesson. |
| `type` | VARCHAR(16) NOT NULL | `TEXT`, `AUDIO`, `IMAGE`, or `VIDEO`. |
| `content_json` | JSONB NOT NULL | Block content (validated per type). |
| `ai_generated` | BOOLEAN NOT NULL (default `false`) | Whether this block was AI-generated. |
| `created_at` | TIMESTAMPTZ NOT NULL | Set on construction. |

### `tasks`

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT PK | Auto-generated. |
| `course_id` | BIGINT NOT NULL | FK to `courses`. |
| `lesson_id` | BIGINT | FK to `lessons` (nullable for standalone tasks). |
| `type` | VARCHAR(32) NOT NULL | One of 10 `TaskType` enum values. |
| `content` | JSONB NOT NULL | Task content (validated per type). |
| `order_index` | INT NOT NULL (default `0`) | Position within the lesson. |
| `title` | VARCHAR(500) | Optional task title. |
| `status` | VARCHAR(32) NOT NULL (default `DRAFT`) | `DRAFT`, `AI_GENERATED`. |
| `ai_generated` | BOOLEAN NOT NULL (default `false`) | Whether AI-generated. |
| `generation_metadata` | JSONB | AI generation metadata (opaque). |
| `unlock_mode` | VARCHAR(16) NOT NULL (default `FREE`) | `FREE`, `SEQUENTIAL`, or `PREREQUISITE`. |
| `prerequisite_task_id` | BIGINT | For `PREREQUISITE` mode: the task that must be completed first. |
| `required_score` | INT | For `PREREQUISITE` mode: minimum score (0–100). |
| `prompt_template_id` | BIGINT | Optional reference to an AI prompt template. |
| `created_at` | TIMESTAMPTZ NOT NULL | Set by `@CreationTimestamp`. |
| `updated_at` | TIMESTAMPTZ NOT NULL | Set by `@UpdateTimestamp`. |

### `lesson_access`

| Column | Type | Description |
|---|---|---|
| `lesson_id` | BIGINT PK (composite) | FK to `lessons`. |
| `student_id` | BIGINT PK (composite) | User ID of the student. |
| `granted_at` | TIMESTAMPTZ NOT NULL | Set on construction. |

Grants individual student access to a specific lesson (beyond group-based access).

### `groups`

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT PK | Auto-generated. |
| `name` | VARCHAR(255) NOT NULL | Group name. |
| `teacher_id` | BIGINT NOT NULL | ID of the owning teacher. |
| `created_at` | TIMESTAMPTZ NOT NULL | Set by `@CreationTimestamp`. |

### `user_groups`

| Column | Type | Description |
|---|---|---|
| `user_id` | BIGINT PK (composite) | Student user ID. |
| `group_id` | BIGINT PK (composite) | FK to `groups`. |

Student membership in a group.

### `group_courses`

| Column | Type | Description |
|---|---|---|
| `group_id` | BIGINT PK (composite) | FK to `groups`. |
| `course_id` | BIGINT PK (composite) | FK to `courses`. |

Assignment of a course to a group.

### `course_teachers`

| Column | Type | Description |
|---|---|---|
| `course_id` | BIGINT PK (composite) | FK to `courses`. |
| `user_id` | BIGINT PK (composite) | Teacher user ID. |
| `role` | VARCHAR(16) NOT NULL (default `OWNER`) | Teacher role in this course. |
| `added_at` | TIMESTAMPTZ NOT NULL | Set on construction. |

Co-teacher assignments. Used by `CourseAccessChecker` to allow non-author teachers to access a course.

### `users` (shared, read-only)

Not owned by this service. Used in `StudentService` (native SQL) and `UserSummaryRepository` (JDBC) to resolve active student users.

### `roles` (shared, read-only)

Not owned by this service. Referenced in native SQL queries to filter by role name.

---

## Flyway Migrations

Flyway is **disabled** (`spring.flyway.enabled=false`). The schema is created and maintained by `auth-service` Flyway migrations. The file `V2__create_content_learning_schema.sql` in the auth-service creates all tables listed above.

---

## Tests

All test classes are in `src/test/java/com/lms/content/`.

### `service/CourseServiceTest`

Unit test for `CourseService` using Mockito. Covers:
- `create` with TEACHER principal: `authorId` is set from the JWT principal.
- `create` with STUDENT principal: throws `ApiBusinessException` (403).
- `delete` by the owning teacher: succeeds.
- `delete` by a different teacher: throws 403.
- `delete` by ADMIN: succeeds (any course).
- `findById` for a non-existent ID: throws 404.

### `validation/ContentJsonValidatorTest`

Unit test for `ContentJsonValidator` with no Spring context. Covers all 10 task types with valid inputs and all mandatory-field violations. Specifically tests:
- `FILL_BLANKS`: valid; missing `text`; missing `answers`.
- `TRUE_FALSE`: valid; missing `questions`; missing `correctOptions`; empty `correctOptions`.
- `VIDEO`: valid; missing `videoUrl`.
- `TEXT`: valid; missing `level`; blank `level`.
- `TRANSLATION`: valid; missing `instructions`; blank `instructions`.
- `DEBATES`: valid; missing `topic`; missing `botRole`.
- Null content map: throws.
- Empty content map: throws.

### `service/GroupServiceBulkAddTest`

Unit test for `GroupService.bulkAddStudents` using Mockito. Covers:
- All new valid students: all appear in `added`.
- Mixed IDs (existing member, new student, unknown ID): correct categorisation.
- Second call with same IDs: all move to `alreadyInGroup` (idempotency).
- Non-owner TEACHER: throws 403.
- STUDENT caller: throws 403.
- ADMIN caller: succeeds on any group.
- More than 200 entries: throws 400.
- Duplicate IDs in request: deduplicated (2 saves for 3 IDs where 2 are unique).
- Teacher-role user ID in request: appears in `notFound` (not a STUDENT).
- Group not found: throws 404.

---

## How to Run Locally

```bash
# 1. Copy and edit the environment file
cp .env.example .env
# Edit DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET to match your setup.
# JWT_SECRET must match auth-service and gateway-service.

# 2. Start PostgreSQL and ensure the schema is already created by auth-service migrations.

# 3. Run the service
mvn spring-boot:run
```

The service starts on `http://localhost:8082`.

Swagger UI: `http://localhost:8082/swagger-ui.html`

Actuator health: `http://localhost:8082/actuator/health`

---

## Docker Build and Run

The Dockerfile uses a two-stage build.

**Stage 1 (build):** `eclipse-temurin:17-jdk-alpine` — installs Maven, runs `mvn -q -B package -DskipTests`, produces the fat JAR.

**Stage 2 (runtime):** `eclipse-temurin:17-jre-alpine` — creates a `spring:spring` non-root user, copies the JAR, exposes port `8082`.

```bash
# Build the image (run from app/content-service/)
docker build -t content-service:local .

# Create the shared network (once)
docker network create lms-net

# Run the container
docker run --rm --name content-service \
  --network lms-net \
  -p 8082:8082 \
  --env-file .env \
  content-service:local
```

**Important `.env` values for Docker:**
- `DB_URL` — use the PostgreSQL container hostname, e.g. `jdbc:postgresql://postgres:5432/lms`.
- `JWT_SECRET` — must match auth-service and gateway-service containers.
- `LEARNING_SERVICE_URL` — e.g. `http://learning-service:8083`.
- `AI_SERVICE_URL` — e.g. `http://ai-service:8084`.

**Gateway configuration** — in `gateway-service/.env` set:
```env
CONTENT_SERVICE_URL=http://content-service:8082
```

Restart the gateway after changing env. The gateway proxies `/api/v1/content/**` to this service.
