# — Платформа изучения английского языка с ИИ

Веб-платформа для ESL-обучения с автоматической проверкой заданий через LLM и ручной валидацией преподавателем. Бэкенд построен на микросервисной архитектуре (Java / Spring Boot), фронтенд — React SPA.

## Сервисы

| Сервис | Порт | Назначение |
|--------|------|------------|
| `gateway-service` | 8080 | Единая точка входа: JWT, CORS, rate limiting, маршрутизация |
| `auth-service` | 8081 | Аутентификация, профили, роли, impersonation; Flyway-миграции |
| `content-service` | 8082 | Курсы, топики, уроки, задания, группы, студенты |
| `learning-service` | 8083 | Сабмит ответов, rule-based и AI-проверка, результаты |
| `ai-service` | 8084 | Внутренний LLM-оценщик; журнал вызовов, генерация контента |

Фронтенд находится в `../frontend/` и общается только с gateway на `VITE_API_BASE_URL`.

## Типы заданий

`FILL_BLANKS` · `TRUE_FALSE` · `TEXT` · `TRANSLATION` · `VIDEO` · `DEBATES` · `SPEAKING`

Закрытые типы (`FILL_BLANKS`, `TRUE_FALSE`) проверяются rule-based чекером без обращения к LLM. Открытые типы передаются в `ai-service`, который вызывает OpenAI-совместимый провайдер.

## Быстрый старт

1. Скопировать `.env.example` → `.env` в каждом сервисе и заполнить переменные.
2. Запустить PostgreSQL и установить строку подключения в `auth-service` (он накатывает Flyway-миграции для всей БД).
3. Поднять сервисы:

```bash
# из корня app/
mvn clean package -DskipTests
java -jar gateway-service/target/*.jar &
java -jar auth-service/target/*.jar &
java -jar content-service/target/*.jar &
java -jar learning-service/target/*.jar &
java -jar ai-service/target/*.jar &
```

Или через Docker — см. [`DOCKER.md`](DOCKER.md).

## Переменные окружения (основные)

| Переменная | Где используется | Описание |
|------------|-----------------|----------|
| `JWT_SECRET` | gateway, auth, content, learning | Общий секрет подписи JWT |
| `DATABASE_URL` | auth, content, learning | JDBC URL PostgreSQL |
| `FRONTEND_ORIGIN` | gateway | Разрешённый CORS origin |
| `AI_SERVICE_URL` | learning | URL ai-service (по умолчанию `http://localhost:8084`) |
| `LLM_API_BASE_URL` | ai-service | База OpenAI-совместимого API |
| `LLM_MODEL` | ai-service | Имя модели |
| `LLM_API_KEY` | ai-service | Ключ провайдера |
| `EMAIL_MODE` | auth-service | `SMTP` — реальная отправка, `LOG` — только лог |

Полный список переменных — в `.env.example` каждого сервиса.

## Документация

- [Журнал изменений](CHANGELOG.md)
- [Полная спецификация проекта](../doc/PROJECT_DOCUMENTATION.md)
- [Схема базы данных](../doc/DATABASE_SCHEMA.md)
- [Docker и сетевые имена](DOCKER.md)
- Swagger UI доступен на каждом сервисе по пути `/swagger-ui.html`

## Структура репозитория

```
app/
├── gateway-service/
├── auth-service/
├── content-service/
├── learning-service/
├── ai-service/
├── CHANGELOG.md
└── README.md          ← этот файл

../frontend/           ← React SPA (отдельный модуль)
../doc/                ← Проектная документация
```
