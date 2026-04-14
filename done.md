# LMS backend (`app/`) — что реализовано

Краткий снимок текущего состояния микросервисов в каталоге `app`: публичные маршруты через **Gateway**, схема БД через **Flyway в auth-service**, **AI Service** — отдельный модуль `ai-service`; learning вызывает его по HTTP на `AI_SERVICE_URL` (не через gateway).

---

## Общая схема

| Сервис | Порт по умолчанию | Роль |
|--------|-------------------|------|
| gateway-service | 8080 | Единая точка входа для SPA: JWT, CORS, rate limit, `X-Request-Id`, прокси на auth/content/learning |
| auth-service | 8081 | Регистрация/логин/refresh JWT, `/me`, админ: пользователи, impersonation; **Flyway-миграции** общей PostgreSQL |
| content-service | 8082 | Курсы, группы, задания (tasks), валидация JSON контента по типу; **internal** API проверки доступа для learning |
| learning-service | 8083 | Сабмит ответов, проверка (rule-based + вызов AI), результаты, валидация учителем |
| ai-service | 8084 | Внутренний LLM-оценщик: `POST /internal/ai/evaluate`, OpenAI-совместимый провайдер через env |
| **frontend** (React SPA) | 3000 (dev) / 80 (Docker nginx) | UI в каталоге `../frontend`; HTTP только к **Gateway** (`VITE_API_BASE_URL`) |

`JWT_SECRET` должен совпадать у auth, content и learning (и gateway для проверки access token). AI Service JWT не использует.

---

## Frontend (`../frontend/`)

**Назначение:** React + TypeScript SPA (Vite). Все запросы к API идут только на **API Gateway** (`/api/v1/...`); внутренние сервисы и LLM с клиента не вызываются.

**Стек:** React 18, React Router v6, Axios (Bearer + refresh при 401), TanStack Query, Tailwind CSS. Общие компоненты в `src/components/ui/`.

**Аутентификация:** `localStorage` — access/refresh токены; после логина — `GET /api/v1/auth/me`. **Impersonation:** баннер «просмотр от имени…», выход через `POST /api/v1/admin/impersonate/stop`.

**Маршруты по ролям:**

| Зона | Примеры путей |
|------|----------------|
| Публичные | `/login`, `/register` |
| STUDENT | `/student/dashboard`, `/student/courses`, `/student/courses/:id`, `/student/tasks/:id`, `/student/history` |
| TEACHER | `/teacher/courses`, `/teacher/courses/new`, `/teacher/courses/:id/edit`, `/teacher/tasks/new`, `/teacher/tasks/:id/edit`, `/teacher/groups`, `/teacher/tasks/:id/results` |
| ADMIN | `/admin/users`, `/admin/users/:userId` |

**Типы заданий (UI):** отдельные компоненты под `FILL_BLANKS`, `TRUE_FALSE`, `VIDEO` (iframe + форма + ожидание AI), `TEXT`, `TRANSLATION`, `DEBATES` (чат, индикатор «AI печатает», кнопка завершения сессии).

**Переменные окружения (сборка / dev):** `VITE_API_BASE_URL` — базовый URL Gateway (по умолчанию в `.env.example`: `http://localhost:8080`). Секреты LLM и системные промпты на клиенте **не** хранятся.

**Docker:** образ на базе nginx (мульти-стадийный build), см. **`frontend/DOCKER.md`** и **`frontend/Dockerfile`**.

**Документация:** `frontend/README.md`, `frontend/DOCKER.md`.

---

## API Gateway (`gateway-service`)

**Маршрутизация Spring Cloud Gateway:**

- `/api/v1/auth/**` → auth-service  
- `/api/v1/content/**` → content-service  
- `/api/v1/learning/**` → learning-service  

**Публично без JWT (только POST):**

- `/api/v1/auth/register`
- `/api/v1/auth/login`
- `/api/v1/auth/refresh`

Все остальные пути под `/api/**` требуют заголовок `Authorization: Bearer <access JWT>`. При успехе gateway добавляет для downstream: `X-User-Id`, `X-User-Role`.

**Дополнительно:** глобальные фильтры — `X-Request-Id` (генерация/проброс), JWT-валидация, CORS (origin из `FRONTEND_ORIGIN`), rate limiting (`RATE_LIMIT_RPS`, по умолчанию 20 req/s). Actuator: `health`, `info`. SpringDoc/Swagger UI включены на gateway.

Внешний **AI Service не проксируется** gateway: вызов только из learning-service на `AI_SERVICE_URL` (по умолчанию `http://localhost:8084`).

---

## Auth Service (`auth-service`)

**Базовые эндпоинты** (`/api/v1/auth`):

| Метод | Путь | Описание |
|-------|------|----------|
| POST | `/register` | Регистрация; роль фиксирована **STUDENT** |
| POST | `/login` | Выдача access + refresh JWT |
| POST | `/refresh` | Ротация refresh, новая пара токенов |
| GET | `/me` | Текущий пользователь по access token |

**Админ** (`ROLE_ADMIN`):

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/api/v1/admin/users` | Список пользователей (пагинация Spring Data) |
| PATCH | `/api/v1/admin/users/{id}` | Смена роли и/или `active` |
| POST | `/api/v1/admin/impersonate` | Выдача access token от имени другого пользователя (аудит) |
| POST | `/api/v1/admin/impersonate/stop` | Завершение impersonation (204) |

При активном impersonation админские операции со списком/patch пользователей запрещены (ошибка бизнес-логики).

**Реализация:** BCrypt для паролей, stateless JWT, отдельные DTO без утечки `password_hash`.

---

## Content Service (`content-service`)

Все публичные маршруты требуют JWT (через gateway). Внутренний путь `/internal/learning/**` в security разрешён без аутентификации — **только для вызова из docker-сети / доверенной среды**, не через публичный gateway (в `application.yml` gateway маршрута на `/internal` нет).

**Курсы** — `/api/v1/content/courses`

| Метод | Путь | Роли / заметки |
|-------|------|----------------|
| GET | `/` | Список курсов с фильтрацией по роли |
| GET | `/{id}` | Курс по id |
| POST | `/` | Создание — **TEACHER**, **ADMIN** |
| PUT | `/{id}` | Обновление — владелец (teacher) или **ADMIN** |
| DELETE | `/{id}` | Удаление — владелец или **ADMIN** |
| GET | `/{courseId}/topics` | Список заданий курса (пагинация) |

**Группы** — `/api/v1/content/groups`

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/` | Список (teacher — свои, admin — все) |
| GET | `/{id}` | Группа по id |
| POST | `/` | Создание |
| PUT | `/{id}` | Обновление имени |
| DELETE | `/{id}` | Удаление |
| POST | `/{groupId}/students` | Добавить студента (`userId` в теле) |
| DELETE | `/{groupId}/students/{userId}` | Убрать студента |
| POST | `/{groupId}/courses` | Привязать курс к группе |
| DELETE | `/{groupId}/courses/{courseId}` | Отвязать курс |

**Задания** — `/api/v1/content/tasks`

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/` | Список заданий |
| GET | `/{id}` | Задание по id |
| POST | `/` | Создание с валидацией JSONB `content` под `TaskType` |
| PUT | `/{id}` | Обновление |
| DELETE | `/{id}` | Удаление |

**Типы заданий (enum):** `FILL_BLANKS`, `TRUE_FALSE`, `VIDEO`, `TEXT`, `TRANSLATION`, `DEBATES`.

**Internal (learning):**

| Метод | Путь | Ответ |
|-------|------|--------|
| GET | `/internal/learning/access?studentId=&taskId=` | `{ "hasAccess": true/false }` — студент в группе, у которой назначен курс, содержащий задачу |

---

## Learning Service (`learning-service`)

**Публичные API** (`/api/v1/learning`):

| Метод | Путь | Роль | Описание |
|-------|------|------|----------|
| POST | `/tasks/{taskId}/submit` | STUDENT | Отправка ответа; проверка доступа через content internal API |
| GET | `/my-results` | STUDENT | История результатов; опционально `courseId`, `page`, `size` |
| GET | `/tasks/{taskId}/results` | TEACHER | Все результаты по задаче (пагинация) |
| PATCH | `/results/{resultId}/validate` | TEACHER | Переопределение балла/комментария, статус `VALIDATED_BY_TEACHER` |

**Оценивание:**

- **KeyBasedChecker:** `FILL_BLANKS`, `TRUE_FALSE` — правила без LLM.
- **AiBasedChecker:** `TEXT`, `TRANSLATION`, `VIDEO`, `DEBATES` — HTTP `POST` на `{AI_SERVICE_URL}/internal/ai/evaluate` с телом задачи и ответа; prompt codes: `text_evaluation`, `translation_evaluation`, `video_evaluation`, `debates_evaluation`; температура 0.2 (для debates 0.8).

Повторный сабмит запрещён, если результат уже **VALIDATED_BY_TEACHER**.

**Тело `POST .../submit`:** поле `answerContent` десериализуется как произвольный JSON (`JsonNode`): допустимы JSON-массив (например ответы `FILL_BLANKS`/`TRUE_FALSE`), объект или строка, содержащая JSON. В БД колонка `task_results.answer_content` пишется как **jsonb** (маппинг Hibernate `@JdbcTypeCode(JSON)` + `JsonNode`, не `varchar`).

Flyway в learning **отключён** (`flyway.enabled: false`) — схема накатывается auth-service.

**Тесты:** unit-тесты для `KeyBasedChecker`, `AiBasedChecker`, `LearningService`, `LearningController` (MockMvc + `JwtUserPrincipal`).

---

## AI Service (`ai-service`)

**Назначение:** внутренний сервис оценки ответов через LLM. Публично не доступен; вызывается только **learning-service**.

**Эндпоинт** (`/internal/ai`):

| Метод | Путь | Описание |
|-------|------|----------|
| POST | `/evaluate` | Тело: `task_type`, `prompt_template_code`, `task_content`, `student_answer`, `options` (`temperature`, `max_tokens`). Ответ: `score`, `feedback`, `verdict` (для TEXT/TRANSLATION/VIDEO — вердикт из JSON модели; для DEBATES — числовой `score` 0.0–1.0, `verdict` null). |

**Промпты:** четыре шаблона в `resources/prompts/` — `text_evaluation`, `translation_evaluation`, `video_evaluation`, `debates_evaluation` (согласованы с `doc/prompt2.txt`). Подстановка `{{PLACEHOLDER}}` из `task_content`; `student_answer` подставляется в `{{STUDENT_ANSWER}}` и в `{{SESSION_TRANSCRIPT}}` (debates). Незаполненные плейсхолдеры очищаются.

**Провайдер LLM:** один клиент **OpenAI-compatible** (`POST {base}/chat/completions`). Смена провайдера (LM Studio, OpenAI, MiniMax и т.д.) — только переменные окружения, без смены кода:

| Переменная | Назначение |
|------------|------------|
| `LLM_API_BASE_URL` | База API (например `http://localhost:1234/v1` для LM Studio) |
| `LLM_MODEL` | Имя модели |
| `LLM_API_KEY` | Ключ (для LM Studio часто достаточно произвольного значения) |
| `LLM_TIMEOUT_SECONDS` | Таймаут HTTP-клиента к LLM |
| `SERVER_PORT` | Порт сервиса (по умолчанию 8084) |

**Поведение:** парсинг JSON из ответа модели; при необходимости удаляется markdown-обёртка вокруг JSON; при невалидном JSON — структурированная ошибка (`BAD_GATEWAY`), без stack trace клиенту. Логирование latency и usage-токенов при наличии в ответе API. Фильтр `X-Request-Id` (MDC).

**Безопасность:** Spring Security с `permitAll` (внутренняя сеть); секреты LLM только через env.

**Сборка и артефакты:** `pom.xml`, `Dockerfile`, `.env.example`, `README.md`. БД не используется. Actuator: `health`, `info`. SpringDoc на сервисе (внутреннее использование).

**Тесты:** `PromptTemplateServiceTest`, `AiEvaluationServiceTest` (парсинг, fences, маппинг score/verdict, clamp для debates).

---

## База данных

Одна PostgreSQL; миграции лежат в `auth-service`:

- `V1__create_roles_users_impersonation.sql` — роли, пользователи, аудит impersonation  
- `V2__create_content_learning_schema.sql` — `prompt_templates`, `courses`, `groups`, `user_groups`, `group_courses`, `tasks`, `task_results`, и т.д.

---

## Что не входит в этот каталог

- Код **frontend** лежит отдельно в **`../frontend/`**, не внутри `app/`.

---

## Документация и запуск

- Полная спецификация: `doc/PROJECT_DOCUMENTATION.md`  
- Docker и сетевые имена контейнеров (backend): `app/DOCKER.md`  
- Docker и запуск SPA: `frontend/DOCKER.md`  
- У каждого сервиса — `README.md` и `.env.example` (в т.ч. `ai-service` с переменными LLM).  
- Frontend: `frontend/README.md`, `frontend/.env.example`.  
- В `learning-service/README.md` дополнительно описан **запуск образа через Docker** (`docker build` / `docker run`, переменные, `host.docker.internal`).
