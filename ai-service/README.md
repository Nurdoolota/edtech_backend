# ai-service

Internal-only microservice for LLM-based task evaluation and AI content generation. Called exclusively by `learning-service` and `content-service` over the internal Docker network. Not exposed through the API Gateway.

## Stack

- Java 21, Spring Boot 3.2.5
- Spring Web, Spring Security, Spring Data JPA
- PostgreSQL (call log persistence)
- springdoc-openapi 2.5 (Swagger UI at `/swagger-ui.html`)

---

## Endpoints

All endpoints are under `/internal/ai` and are not reachable from outside the Docker network.

### Evaluation

```
POST /internal/ai/evaluate
```

Evaluates a student's answer using the LLM. Request body:

```json
{
  "task_type": "TEXT",
  "prompt_template_code": "text_evaluation",
  "task_content": { "SOURCE_TEXT": "...", "QUESTIONS_OR_INSTRUCTIONS": "..." },
  "student_answer": "...",
  "options": { "temperature": 0.2, "max_tokens": 2048 }
}
```

`options` is optional; defaults are `temperature=0.2`, `max_tokens=2048`.

Response:

```json
{
  "score": 1,
  "feedback": "Good attempt, but...",
  "verdict": "Accepted"
}
```

Score semantics:
- For **TEXT / TRANSLATION / VIDEO**: `score` is `1` (Accepted) or `0` (Rejected), derived from the `verdict` field.
- For **DEBATES**: `score` is a decimal in `[0.0, 1.0]` (clamped, 2 decimal places); `verdict` is `null`.

### Content Generation

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/internal/ai/generate-lesson` | Generate a full lesson JSON from topic + level |
| `POST` | `/internal/ai/generate-task` | Generate a single task by type and context |
| `POST` | `/internal/ai/generate-topic` | Generate a topic outline with N lessons |
| `POST` | `/internal/ai/regenerate-lesson` | Regenerate an existing lesson with optional hints |

All generation endpoints accept an optional `X-User-Id` header (used for call logging). Temperature is fixed at `0.7`, `max_tokens` at `4096`.

#### POST /internal/ai/generate-lesson

```json
{
  "courseId": 1,
  "topicId": 5,
  "topic": "Present Perfect Tense",
  "level": "B1",
  "length": "medium",
  "taskTypes": ["TEXT", "TRANSLATION"],
  "includeTheory": true,
  "instructions": "Focus on real-life examples"
}
```

`courseId`, `topicId`, `topic`, `level` — required. Others optional.

Response: `AiLessonJson`

```json
{
  "title": "...",
  "generationMetadata": "...",
  "blocks": [
    { "type": "THEORY", "contentJson": "...", "orderIndex": 0 }
  ],
  "tasks": [
    { "type": "TEXT", "title": "...", "content": {}, "orderIndex": 1, "unlockMode": "SEQUENTIAL" }
  ]
}
```

#### POST /internal/ai/generate-task

```json
{
  "lessonId": 42,
  "type": "TRANSLATION",
  "context": "Lesson about present perfect",
  "level": "B1"
}
```

`lessonId`, `type`, `level` — required. `context` optional.

Response: `AiTaskJson`

```json
{ "type": "TRANSLATION", "title": "...", "content": {}, "orderIndex": 0, "unlockMode": "SEQUENTIAL" }
```

#### POST /internal/ai/generate-topic

```json
{
  "courseId": 1,
  "topicTitle": "English Tenses",
  "description": "Overview of all English tenses",
  "level": "B2",
  "lessonCount": 5
}
```

`courseId`, `topicTitle`, `level`, `lessonCount` — required. `description` optional.

Response: `AiTopicJson`

```json
{
  "topicTitle": "English Tenses",
  "lessons": [ { "title": "...", "blocks": [], "tasks": [] } ]
}
```

#### POST /internal/ai/regenerate-lesson

```json
{
  "lessonJson": "{...existing lesson JSON...}",
  "hint": "Add more speaking tasks",
  "preserveIds": [10, 11],
  "taskTypes": ["VIDEO", "DEBATES"]
}
```

`lessonJson` — required. `hint`, `preserveIds`, `taskTypes` optional.

Response: `AiLessonJson` (same shape as generate-lesson).

### LLM Health Probe

```
GET /internal/ai/health
```

Sends a minimal `"Respond with OK."` prompt to the LLM and returns connectivity status. Always returns HTTP 200 — the `ai` field signals `UP` or `DOWN`.

```json
{ "ai": "UP", "model": "gpt-4o", "latencyMs": 312 }
```

### Call Log

```
GET /internal/ai/log?page=0&size=50&userId=123&status=ERROR
```

Returns a paginated list of all LLM call records, sorted newest-first. Consumed by `auth-service` admin proxy. Optional filters: `userId`, `status` (`SUCCESS` | `ERROR` | `TIMEOUT`).

Response:

```json
{
  "items": [
    {
      "id": 1,
      "requestId": "uuid",
      "userId": 123,
      "endpoint": "/internal/ai/evaluate",
      "model": "gpt-4o",
      "latencyMs": 820,
      "tokensIn": 512,
      "tokensOut": 128,
      "status": "SUCCESS",
      "error": null,
      "createdAt": "2025-01-01T12:00:00Z"
    }
  ],
  "totalElements": 42,
  "totalPages": 1,
  "page": 0,
  "size": 50
}
```

---

## Prompt Templates

Templates live in `src/main/resources/prompts/` and are loaded at startup into memory by `PromptTemplateService` (`@PostConstruct`). Placeholders use `{{KEY}}` syntax. Unreplaced placeholders are stripped automatically before the prompt is sent.

| Code | File | Use |
|------|------|-----|
| `text_evaluation` | `text_evaluation.txt` | Comprehension, discussion, summary tasks |
| `translation_evaluation` | `translation_evaluation.txt` | Translation tasks |
| `video_evaluation` | `video_evaluation.txt` | Video-based tasks |
| `debates_evaluation` | `debates_evaluation.txt` | Debate / speaking session evaluation |
| `lesson_generation` | `lesson_generation.txt` | Full lesson generation |
| `task_generation` | `task_generation.txt` | Single task generation |
| `topic_generation` | `topic_generation.txt` | Topic outline generation |
| `lesson_regeneration` | `lesson_regeneration.txt` | Lesson regeneration with diff hints |

**Evaluation placeholder injection:**
- `task_content` map keys → `{{KEY}}` (upper-cased automatically)
- `student_answer` → `{{STUDENT_ANSWER}}`
- DEBATES tasks additionally accept `{{SESSION_TRANSCRIPT}}` (same value as `student_answer`)

**Supported task types for evaluation:**

| Task Type | Prompt Code | Recommended Temperature |
|-----------|-------------|------------------------|
| TEXT | `text_evaluation` | 0.0–0.3 |
| TRANSLATION | `translation_evaluation` | 0.0–0.3 |
| VIDEO | `video_evaluation` | 0.0–0.3 |
| DEBATES | `debates_evaluation` | 0.7–1.0 |

---

## Architecture

### LLM Client

`LlmClient` interface defines a single `complete(systemPrompt, temperature, maxTokens)` method. `OpenAiCompatibleClient` implements it via Spring `RestClient`, calling `/chat/completions`. Supports any OpenAI-compatible API (LMStudio, OpenAI, MiniMax, etc.) — switch provider via env vars with no code changes. Logs token usage and latency on each call. Throws `ApiBusinessException` (503) on network failure or empty response.

### Retry + JSON Repair

`AiRetryExecutor` wraps every generation LLM call with:

1. Up to `ai.max-retries` attempts (default 3).
2. On each attempt: calls the LLM supplier and attempts `ObjectMapper.readValue`.
3. On `JsonProcessingException`: triggers one repair attempt via `JsonRepairerImpl` (sends the broken JSON back to the LLM with a fix instruction; temperature 0.0, max 2048 tokens).
4. If repair still fails: throws `JsonRepairException` → propagates immediately as HTTP 502 (no further retry).
5. On network/runtime error: exponential back-off — 1 s → 2 s → 4 s between attempts.
6. After all attempts exhausted: throws `AiServiceException` → HTTP 502.

`AiEvaluationService` handles its own LLM call without `AiRetryExecutor`; it parses the JSON directly and throws `ApiBusinessException` (502) on parse failure.

### Call Logging

`AiCallLogger` writes one row to `ai_call_log` after every LLM call (success and failure). The `X-Request-Id` value is read from MDC (populated by `RequestIdFilter`). API keys in the `error` field are redacted via `ApiKeyRedactor` before persistence. The `log()` method never throws — logging failures are swallowed to avoid masking the original response.

`ApiKeyRedactor` masks two patterns before they reach the DB or log files:
- `Bearer <token>` → `Bearer [REDACTED]`
- `sk-<token>` → `sk-[REDACTED]`

### Request Tracing

`RequestIdFilter` (`OncePerRequestFilter`) reads `X-Request-Id` from every incoming request (or generates a UUID if absent), stores it in MDC under key `requestId`, and echoes it back in the response header. All error responses include the same `requestId` field.

### Security

`SecurityConfig` disables CSRF and permits all requests — authentication is enforced at the network level (internal Docker network only). No JWT validation is performed.

### Error Handling

`GlobalExceptionHandler` maps exceptions to a structured `ApiError` JSON body with `code`, `message`, and `requestId` fields:

| Exception | HTTP Status | Code |
|-----------|-------------|------|
| `ApiBusinessException` | varies (from exception) | from exception |
| `AiServiceException` | 502 | `BAD_GATEWAY` |
| `MethodArgumentNotValidException` | 400 | `VALIDATION_ERROR` |
| `Exception` (catch-all) | 500 | `INTERNAL_ERROR` |

API keys are redacted from all error messages before logging or returning to callers.

---

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8084` | HTTP port |
| `LLM_API_BASE_URL` | `http://localhost:1234/v1` | Provider base URL |
| `LLM_MODEL` | `local-model` | Model name sent in every request |
| `LLM_API_KEY` | `lm-studio` | Bearer token for LLM API |
| `LLM_TIMEOUT_SECONDS` | `28` | HTTP connect + read timeout for LLM calls |
| `AI_MAX_RETRIES` | `3` | Max retry attempts in `AiRetryExecutor` |
| `AI_REQUEST_TIMEOUT_SECONDS` | `60` | Overall request timeout (generation RestTemplate) |
| `AI_PROVIDER` | `local` | Provider label (informational only) |
| `DB_URL` | `jdbc:postgresql://localhost:5432/lms` | PostgreSQL JDBC URL |
| `DB_USERNAME` | `postgres` | DB username |
| `DB_PASSWORD` | `qwerty` | DB password |

Copy `.env.example` to `.env` and fill in values.

### Preconfigured Providers (.env.example)

| Provider | `LLM_API_BASE_URL` | `LLM_MODEL` |
|----------|--------------------|-------------|
| LMStudio (default) | `http://localhost:1234/v1` | `local-model` |
| OpenAI | `https://api.openai.com/v1` | `gpt-4o` |
| MiniMax | `https://api.minimax.chat/v1` | `MiniMax-M2.5` |

---

## Database

Table `ai_call_log` (schema validated at startup via `ddl-auto: validate`):

| Column | Type | Notes |
|--------|------|-------|
| `id` | `bigserial` | PK |
| `request_id` | `varchar(64)` | Correlation ID from `X-Request-Id` |
| `user_id` | `bigint` | Caller user ID (nullable) |
| `endpoint` | `varchar(128)` | Called endpoint path, not null |
| `model` | `varchar(128)` | LLM model name, not null |
| `latency_ms` | `int` | End-to-end latency, not null |
| `tokens_in` | `int` | Prompt tokens (nullable — not all providers return usage) |
| `tokens_out` | `int` | Completion tokens (nullable) |
| `status` | `varchar(16)` | `SUCCESS` / `ERROR` / `TIMEOUT`, not null |
| `error` | `varchar(500)` | Redacted error message |
| `created_at` | `timestamptz` | Set to `Instant.now()` on construction |

Repository query methods: `findByUserId`, `findByStatus`, `findByUserIdAndStatus` (all paginated).

---

## Running Locally

```bash
cp .env.example .env
# edit .env — at minimum set LLM_API_BASE_URL, LLM_MODEL, LLM_API_KEY, DB_URL, DB_USERNAME, DB_PASSWORD
mvn spring-boot:run
```

Spring Actuator health: `GET /actuator/health`

Swagger UI: `http://localhost:8084/swagger-ui.html`

---

## Docker

```dockerfile
# multi-stage build
# stage 1: maven:3.9.6-eclipse-temurin-17 (build)
# stage 2: eclipse-temurin:17-jre (runtime)
# exposes port 8084
docker build -t ai-service .
docker run --env-file .env -p 8084:8084 ai-service
```

> Note: the Dockerfile uses `eclipse-temurin:17-jre` while `pom.xml` targets Java 21 (`java.version = 21`). The service compiles and runs because Java 17 bytecode is a subset; however aligning the Dockerfile to `eclipse-temurin:21-jre` is recommended.

---

## Tests

12 unit test classes, no integration tests (no Testcontainers / real DB required):

| Class | What it covers |
|-------|---------------|
| `AiEvaluationServiceTest` | Evaluation flow, prompt rendering, LLM call, score/verdict mapping, markdown fence stripping |
| `AiGenerationServiceTest` | All four generation methods, variable injection, code-fence stripping |
| `AiGenerationControllerTest` | Controller-layer validation and routing for all generation endpoints |
| `AiHealthServiceTest` | UP / DOWN probe logic, latency measurement |
| `AiHealthControllerTest` | Controller always returns HTTP 200 regardless of LLM status |
| `AiRetryExecutorTest` | Retry count, exponential back-off, JSON-repair trigger, repair failure propagation |
| `JsonRepairerImplTest` | Repair success path, LLM call failure, still-invalid JSON after repair |
| `AiCallLoggerTest` | Log persistence, never-throws contract, API key redaction in error field |
| `ApiKeyRedactorTest` | Bearer and sk- redaction patterns, null input handling |
| `AiLogControllerTest` | Pagination, `userId` / `status` filter combinations, response shape |
| `GlobalExceptionHandlerAiServiceTest` | HTTP status codes and error body for all mapped exception types |
| `PromptTemplateServiceTest` | Template loading at startup, placeholder substitution, unknown template code |

Run all tests:

```bash
mvn test
```
