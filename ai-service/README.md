# ai-service

Internal-only microservice for LLM-based task evaluation. Called exclusively by `learning-service`.

## Endpoint

```
POST /internal/ai/evaluate
```

Not exposed through the API Gateway. Accessible only within the internal Docker network.

## Provider Configuration

Supports any OpenAI-compatible API. Switch provider via env vars only — no code changes needed.

| Variable | Default | Description |
|---|---|---|
| `LLM_API_BASE_URL` | `http://localhost:1234/v1` | Provider base URL |
| `LLM_MODEL` | `local-model` | Model name |
| `LLM_API_KEY` | `lm-studio` | API key (any value for LMStudio) |
| `LLM_TIMEOUT_SECONDS` | `28` | Request timeout |
| `SERVER_PORT` | `8084` | Service port |

Copy `.env.example` to `.env` and fill in values.

## Running locally

```bash
cp .env.example .env
# edit .env as needed
mvn spring-boot:run
```

## Health check

```
GET /actuator/health
```

## Supported task types

| Task Type | Prompt Code | Temperature |
|---|---|---|
| TEXT | `text_evaluation` | 0.0–0.3 |
| TRANSLATION | `translation_evaluation` | 0.0–0.3 |
| VIDEO | `video_evaluation` | 0.0–0.3 |
| DEBATES | `debates_evaluation` | 0.7–1.0 |
