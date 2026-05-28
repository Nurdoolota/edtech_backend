# Журнал изменений — LMS Backend (`app/`)

Все значимые изменения этого проекта документируются здесь.  
Формат основан на [Keep a Changelog](https://keepachangelog.com/ru/1.0.0/).  
Версионирование следует принципам [Semantic Versioning](https://semver.org/lang/ru/).

---

## [0.5.1] — 2026-05-28

### Добавлено

- **GW-02** — Отдельные бакеты rate-limit для чувствительных эндпоинтов (`/auth/login`, `/auth/register`, `/auth/forgot-password`, `/auth/reset-password`): более жёсткие лимиты в сравнении с глобальными настройками (`RATE_LIMIT_RPS`).
- **GW-01** — Публичные маршруты в gateway для потока восстановления пароля (`/api/v1/auth/forgot-password`, `/api/v1/auth/reset-password`) и health-чек AI-сервиса (`/api/v1/ai/health`) без требования JWT.

---

## [0.5.0] — 2026-05-26

### Добавлено — AI Service

- **AI-04** — Эндпоинты генерации контента (`POST /internal/ai/generate`): создание текстов заданий через LLM с поддержкой шаблонов и подстановки параметров.
- **AI-03** — Журнал вызовов AI: запись каждого запроса в лог (`POST /internal/ai/log`) и внутренний API просмотра истории (`GET /internal/ai/log`) с фильтрацией по дате и типу задания.
- **AI-02** — Утилиты надёжности: `AiRetryExecutor` (повтор запроса при сбое провайдера с экспоненциальной задержкой) и `JsonRepairer` (восстановление частично сломанного JSON-ответа модели, удаление markdown-обёрток).
- **AI-01** — Внутренний health-check эндпоинт (`GET /internal/ai/health`): проверка доступности LLM-провайдера и возврат латентности последнего ping-запроса.

---

## [0.4.0] — 2026-05-22

### Добавлено — Learning Service

- **LEARN-04** — Поддержка загрузки аудиофайлов для нового типа заданий `SPEAKING`: multipart-upload, хранение пути к файлу в `task_results.answer_content`.
- **LEARN-03** — Внутренние эндпоинты для межсервисного взаимодействия: `GET /internal/learning/results` (результаты по заданию) и `GET /internal/learning/student-stats` (статистика прогресса студента).
- **LEARN-02** — Принудительное соблюдение порядка прохождения заданий: режимы `SEQUENTIAL` (строгая очерёдность в рамках урока) и `PREREQUISITE` (разблокировка по списку предшественников); при нарушении — `403 Forbidden`.
- **LEARN-01** — Разделение поля оценки в `TaskResult` на два независимых: `ai_score` (выставляется AI-сервисом) и `teacher_score` (выставляется при валидации преподавателем); исторические записи мигрированы.

---

## [0.3.3] — 2026-05-21

### Добавлено — Content Service

- **CONTENT-10** — Полный импорт/экспорт курса: `GET /api/v1/content/courses/{id}/export` возвращает ZIP-архив с JSON-манифестом, медиафайлами и заданиями; `POST /api/v1/content/courses/import` разворачивает архив и создаёт курс с сохранением структуры.
- **CONTENT-09** — Прокси-эндпоинт для генерации контента через AI (`POST /api/v1/content/ai/generate`): передаёт запрос в AI Service и возвращает готовый текст/JSON задания преподавателю.

---

## [0.3.2] — 2026-05-19

### Добавлено — Content Service

- **CONTENT-08** — Массовое добавление студентов в группу (`POST /api/v1/content/groups/{id}/students/bulk`): принимает список `userId`, пропускает дубли, возвращает счётчик добавленных.
- **CONTENT-07** — Управление студентами: `GET /api/v1/content/students` (список с пагинацией и поиском по email/имени) и `GET /api/v1/content/students/{id}` (детальная карточка: группы, прогресс по курсам).
- **CONTENT-06** — Дерево курса (`GET /api/v1/content/courses/{id}/tree`): иерархический ответ «курс → топики → уроки → задания» за один запрос; статистика курса (`GET /api/v1/content/courses/{id}/stats`): количество студентов, средний балл, процент завершения.

---

## [0.3.1] — 2026-05-15

### Добавлено — Content Service

- **CONTENT-05** — Эндпоинт доступных заданий урока (`GET /api/v1/content/lessons/{id}/available-tasks`): кросс-сервисная логика — запрашивает прогресс из Learning Service и возвращает только разблокированные задания с учётом режима unlock студента.
- **CONTENT-04** — Расширенное управление заданиями: режимы разблокировки (`FREE`, `SEQUENTIAL`, `PREREQUISITE`), указание списка предшественников (`prerequisite_task_ids`); при сохранении — обнаружение циклических зависимостей (DFS, ошибка `422 Unprocessable Entity`).

---

## [0.3.0] — 2026-05-12

### Добавлено — Content Service

- **CONTENT-03** — CRUD блоков урока (`/api/v1/content/lessons/{lessonId}/blocks`): типы блоков `TEXT`, `IMAGE`, `VIDEO`, `AUDIO`; серверная валидация JSONB-контента по типу блока.
- **CONTENT-02** — Полное управление уроками (`/api/v1/content/lessons`): создание, редактирование, удаление, изменение порядка (`PATCH /lessons/{id}/reorder`); уроки привязаны к топику и курсу.
- **CONTENT-01** — CRUD топиков (`/api/v1/content/topics`) с перестановкой порядка: `PATCH /topics/{id}/reorder` обновляет поле `position` и сдвигает остальные топики атомарно.

---

## [0.2.1] — 2026-05-08

### Добавлено — Auth Service

- **AUTH-07** — Прокси-эндпоинт для администратора: `GET /api/v1/admin/ai-log` — запрашивает историю вызовов AI Service и возвращает её с пагинацией.
- **AUTH-06** — Смена email в два шага: `POST /api/v1/auth/change-email/request` (письмо с токеном подтверждения) → `POST /api/v1/auth/change-email/confirm` (применение нового адреса после верификации).
- **AUTH-05** — Поток восстановления пароля: `POST /api/v1/auth/forgot-password` (отправка письма с одноразовой ссылкой) и `POST /api/v1/auth/reset-password` (применение нового пароля по токену с TTL 15 минут).

---

## [0.2.0] — 2026-05-05

### Добавлено — Auth Service

- **AUTH-04** — Переключаемый `EmailService` с шаблонами Thymeleaf: два режима — `SMTP` (реальная отправка через `spring-boot-starter-mail`) и `LOG` (вывод в консоль для dev-окружения); выбор через `EMAIL_MODE` env-переменную.
- **AUTH-03** — Смена пароля (`POST /api/v1/auth/change-password`): принимает `currentPassword` и `newPassword`; проверка BCrypt, ротация хэша, инвалидация текущего access-токена не требуется (stateless JWT).
- **AUTH-02** — Загрузка и отдача аватара: `POST /api/v1/auth/me/avatar` (multipart, сохранение в `upload-dir`), `GET /api/v1/auth/avatars/{filename}` (статика); ссылка на аватар включена в ответ `/me`.
- **AUTH-01** — Управление профилем: `PATCH /api/v1/auth/me` (обновление `firstName`, `lastName`) и `DELETE /api/v1/auth/me` (деактивация аккаунта, `is_active = false`).

---

## [0.1.3] — 2026-04-14

### Исправлено

- **fix(auth, content, gateway)** — Устранены ошибки логики и несоответствия эндпоинтов: корректное наследование ролей в security-конфигурации auth-service, правильная прокси-маршрутизация в gateway, согласованные HTTP-коды ответов в content-service.

---

## [0.1.2] — 2026-04-12

### Добавлено

- Начальная проектная документация: описание архитектуры, схема БД, руководство по запуску (`doc/`).

### Исправлено

- **fix(content-service)** — Добавлен обработчик internal-эндпоинта для проверки доступа из learning-service (`GET /internal/learning/access`).

---

## [0.1.1] — 2026-04-11

### Исправлено

- **fix(learning-service)** — Ответы студентов теперь сохраняются как JSONB-совместимый JSON: Hibernate-маппинг изменён с `VARCHAR` на `@JdbcTypeCode(JSON)` + `JsonNode`; устранена ошибка сериализации для `FILL_BLANKS` и `TRUE_FALSE`.

---

## [0.1.0] — 2026-04-10

### Добавлено — Первоначальная реализация

**Общая инфраструктура**
- Инициализация монорепозитория: Maven multi-module проект с общей конфигурацией (`pom.xml`).
- PostgreSQL как единая БД; Flyway-миграции в `auth-service`:
  - `V1__create_roles_users_impersonation.sql` — таблицы `roles`, `users`, аудит impersonation.
  - `V2__create_content_learning_schema.sql` — `courses`, `groups`, `user_groups`, `group_courses`, `tasks`, `task_results`, `prompt_templates`.

**Gateway Service** (порт 8080)
- Spring Cloud Gateway: маршрутизация `/api/v1/auth/**`, `/api/v1/content/**`, `/api/v1/learning/**`.
- JWT-валидация access-токена; проброс `X-User-Id`, `X-User-Role` в downstream-сервисы.
- Глобальные фильтры: генерация/проброс `X-Request-Id`, CORS (origin из `FRONTEND_ORIGIN`), rate limiting (`RATE_LIMIT_RPS`, по умолчанию 20 req/s).
- Публичные эндпоинты без JWT: `POST /auth/register`, `POST /auth/login`, `POST /auth/refresh`.
- SpringDoc/Swagger UI; Actuator (`health`, `info`).

**Auth Service** (порт 8081)
- Регистрация (`POST /register`) — роль фиксирована `STUDENT`; BCrypt для паролей.
- Аутентификация (`POST /login`) — выдача пары access + refresh JWT.
- Ротация токенов (`POST /refresh`).
- Текущий пользователь (`GET /me`).
- Админ-операции (`ROLE_ADMIN`): список пользователей с пагинацией (`GET /admin/users`), смена роли/блокировка (`PATCH /admin/users/{id}`), impersonation (`POST /admin/impersonate`, `POST /admin/impersonate/stop`) с аудитом.

**Content Service** (порт 8082)
- CRUD курсов (`/api/v1/content/courses`): список с фильтрацией по роли, создание/обновление/удаление, топики курса с пагинацией.
- CRUD групп (`/api/v1/content/groups`): управление студентами и привязка курсов.
- CRUD заданий (`/api/v1/content/tasks`): типы `FILL_BLANKS`, `TRUE_FALSE`, `VIDEO`, `TEXT`, `TRANSLATION`, `DEBATES`; серверная валидация JSONB `content` по типу.
- Internal API для learning-service: `GET /internal/learning/access?studentId=&taskId=` — проверка наличия доступа студента к заданию через группы.

**Learning Service** (порт 8083)
- Сабмит ответа (`POST /api/v1/learning/tasks/{taskId}/submit`): проверка доступа через content internal API.
- Паттерн Strategy для выбора чекера по `TaskType`:
  - `KeyBasedChecker` — `FILL_BLANKS`, `TRUE_FALSE` (rule-based, без LLM).
  - `AiBasedChecker` — `TEXT`, `TRANSLATION`, `VIDEO`, `DEBATES` (HTTP → AI Service).
- История результатов студента (`GET /api/v1/learning/my-results`).
- Результаты по заданию для преподавателя (`GET /api/v1/learning/tasks/{taskId}/results`).
- Валидация преподавателем (`PATCH /api/v1/learning/results/{resultId}/validate`): переопределение балла, статус `VALIDATED_BY_TEACHER`.
- Unit-тесты: `KeyBasedChecker`, `AiBasedChecker`, `LearningService`, `LearningController`.

**AI Service** (порт 8084)
- Внутренний эндпоинт оценки (`POST /internal/ai/evaluate`): поля `task_type`, `prompt_template_code`, `task_content`, `student_answer`, `options`.
- Четыре промпт-шаблона в `resources/prompts/`: `text_evaluation`, `translation_evaluation`, `video_evaluation`, `debates_evaluation`.
- OpenAI-совместимый HTTP-клиент; смена провайдера через env (`LLM_API_BASE_URL`, `LLM_MODEL`, `LLM_API_KEY`).
- Устойчивый парсинг JSON-ответа модели: удаление markdown-обёрток, структурированная ошибка при невалидном JSON.
- Spring Security `permitAll` (внутренняя сеть); фильтр `X-Request-Id` в MDC.
- Unit-тесты: `PromptTemplateServiceTest`, `AiEvaluationServiceTest`.

---

[0.5.1]: https://github.com/Nurdoolot/lms-app/compare/v0.5.0...v0.5.1
[0.5.0]: https://github.com/Nurdoolot/lms-app/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/Nurdoolot/lms-app/compare/v0.3.3...v0.4.0
[0.3.3]: https://github.com/Nurdoolot/lms-app/compare/v0.3.2...v0.3.3
[0.3.2]: https://github.com/Nurdoolot/lms-app/compare/v0.3.1...v0.3.2
[0.3.1]: https://github.com/Nurdoolot/lms-app/compare/v0.3.0...v0.3.1
[0.3.0]: https://github.com/Nurdoolot/lms-app/compare/v0.2.1...v0.3.0
[0.2.1]: https://github.com/Nurdoolot/lms-app/compare/v0.2.0...v0.2.1
[0.2.0]: https://github.com/Nurdoolot/lms-app/compare/v0.1.3...v0.2.0
[0.1.3]: https://github.com/Nurdoolot/lms-app/compare/v0.1.2...v0.1.3
[0.1.2]: https://github.com/Nurdoolot/lms-app/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/Nurdoolot/lms-app/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/Nurdoolot/lms-app/releases/tag/v0.1.0
