# Запуск сервисов в одной Docker-сети

Контейнеры не видят `localhost` друг друга: `localhost` внутри контейнера — это только он сам. Чтобы **gateway** ходил в **auth** по имени `http://auth-service:8081`, оба процесса должны быть в **одной пользовательской сети**, а у контейнера auth должно быть **имя** `auth-service` (или сетевой алиас с тем же именем).

То же правило для **content**, **learning**, **ai-service** (для `AI_SERVICE_URL` из learning) и **PostgreSQL**: внутри сети используйте **имена контейнеров** как хосты в `DB_URL` и `*_SERVICE_URL`.

## Сводка: один стек в сети `lms-net`

| Сервис | Имя контейнера (DNS) | Порт на хосте | Назначение |
|--------|----------------------|---------------|------------|
| PostgreSQL | `postgres` | `5432` (опционально) | Общая БД для auth, content, learning |
| auth-service | `auth-service` | `8081` | Аутентификация, миграции Flyway |
| content-service | `content-service` | `8082` | Контент и проверка доступа |
| learning-service | `learning-service` | `8083` | Сабмиты и оценка заданий |
| ai-service | `ai-service` | `8084` | Внутренний LLM-оценщик (не через gateway) |
| gateway-service | любое (например `gateway-service`) | `8080` | Единая точка входа API |

`JWT_SECRET` в **auth**, **content** и **learning** должен **совпадать**. **ai-service** JWT не использует; для LLM задаются `LLM_*` (см. раздел про **ai-service**).

## Предварительно

1. Установлен **Docker** (Docker Desktop на Windows/macOS или Docker Engine на Linux).
2. Собраны образы из каталогов сервисов:

```bash
cd auth-service
docker build -t auth-service:local .

cd ../content-service
docker build -t content-service:local .

cd ../learning-service
docker build -t learning-service:local .

cd ../ai-service
docker build -t ai-service:local .

cd ../gateway-service
docker build -t gateway-service:local .
```

3. Подготовлены файлы **`.env`** (скопируйте из `.env.example` в каждом сервисе и выставьте значения). Секреты не коммитьте. Для **ai-service** обязательно настройте **`LLM_*`** под ваш провайдер (LM Studio на хосте, облако и т.д.).

## База данных и auth

`auth-service` подключается к PostgreSQL по **`DB_URL`**. Если Postgres тоже в Docker и в **той же сети**, в `.env` для auth укажите хост **имени контейнера Postgres**, например:

`DB_URL=jdbc:postgresql://postgres:5432/lms`

Если Postgres на хосте (Windows), из контейнера auth часто используют:

`DB_URL=jdbc:postgresql://host.docker.internal:5432/lms`

Пока БД недоступна из контейнера auth, регистрация и миграции Flyway завершатся ошибкой.

## Одна пользовательская сеть

Создайте сеть один раз (имя можно заменить):

```bash
docker network create lms-net
```

## AI Service (`ai-service`) и доступ к LLM из контейнера

**ai-service** не ходит в PostgreSQL. Он вызывает внешний **OpenAI-compatible** API (`/v1/chat/completions`). Переменные см. в **`ai-service/.env.example`**.

Типичные случаи:

| Где запущен LLM (LM Studio, vLLM и т.п.) | Что указать в `ai-service` `.env` |
|----------------------------------------|-----------------------------------|
| На **хосте** (Windows/macOS Docker Desktop) | `LLM_API_BASE_URL=http://host.docker.internal:1234/v1` (порт подставьте свой) |
| На **хосте** (Linux без `host.docker.internal`) | Запускайте контейнер ai с `--add-host=host.docker.internal:host-gateway` и тем же URL, либо укажите IP хоста в bridge-сети |
| В **другом контейнере** в `lms-net` с именем, например, `llm` | `LLM_API_BASE_URL=http://llm:8000/v1` (путь и порт — как у вашего образа) |

**learning-service** обращается к AI по **`AI_SERVICE_URL`**:

- Если **ai-service** в той же сети `lms-net` с именем контейнера `ai-service`:  
  `AI_SERVICE_URL=http://ai-service:8084`
- Если AI запущен **на хосте** без Docker:  
  `AI_SERVICE_URL=http://host.docker.internal:8084` и контейнер **ai-service** не нужен (но тогда сам процесс ai должен слушать порт на хосте).

Смена облачного провайдера (OpenAI, MiniMax и т.д.) — только **`LLM_API_BASE_URL`**, **`LLM_MODEL`**, **`LLM_API_KEY`** в `.env` ai-service; образ тот же.

---

## Полный стек: PostgreSQL и все backend-сервисы в `lms-net`

Ниже — один сценарий, когда БД и **auth**, **content**, **learning**, **ai** (при необходимости), **gateway** работают в **одной** сети; имена контейнеров совпадают со сводной таблицей в начале документа.

### PostgreSQL в той же сети

Пример с официальным образом (пароль и БД подставьте свои):

```bash
docker run -d --name postgres --network lms-net \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=qwerty \
  -e POSTGRES_DB=lms \
  -p 5432:5432 \
  postgres:16-alpine
```

В **`auth-service`**, **`content-service`** и **`learning-service`** в `.env` задайте одну и ту же строку подключения:

`DB_URL=jdbc:postgresql://postgres:5432/lms`

(плюс `DB_USERNAME` / `DB_PASSWORD`, согласованные с `POSTGRES_*` контейнера).

### Запуск сервисов (имена и сеть обязательны)

**auth-service** — первым из приложений (Flyway создаёт схему):

```bash
cd auth-service
docker run --rm --name auth-service --network lms-net -p 8081:8081 --env-file .env auth-service:local
```

**content-service:**

```bash
cd content-service
docker run --rm --name content-service --network lms-net -p 8082:8082 --env-file .env content-service:local
```

**ai-service** (если оценка через LLM нужна в Docker-стеке) — **до** learning, чтобы при старте learning уже резолвил `AI_SERVICE_URL`. В `.env` ai-service задайте доступ к LLM (см. раздел «AI Service и доступ к LLM» выше).

```bash
cd ai-service
docker run --rm --name ai-service --network lms-net -p 8084:8084 --env-file .env ai-service:local
```

**learning-service** — в `.env` укажите соседей по сети (без слэша в конце):

```env
CONTENT_SERVICE_URL=http://content-service:8082
AI_SERVICE_URL=http://ai-service:8084
```

Если **ai-service** не в Docker, а процесс AI запущен **на хосте** на порту 8084:

```env
AI_SERVICE_URL=http://host.docker.internal:8084
```

На Linux при отсутствии `host.docker.internal` добавьте к `docker run` learning: `--add-host=host.docker.internal:host-gateway`.

```bash
cd learning-service
docker run --rm --name learning-service --network lms-net -p 8083:8083 --env-file .env learning-service:local
```

**gateway-service** — последним; во **`gateway-service/.env`** все три downstream-URL на имена контейнеров:

```env
AUTH_SERVICE_URL=http://auth-service:8081
CONTENT_SERVICE_URL=http://content-service:8082
LEARNING_SERVICE_URL=http://learning-service:8083
```

**`JWT_SECRET`** в gateway должен совпадать с **auth** (и с **content** / **learning**, где он требуется).

```bash
cd gateway-service
docker run --rm --name gateway-service --network lms-net -p 8080:8080 --env-file .env gateway-service:local
```

**Порядок запуска:** сеть → Postgres (если БД в Docker) → **auth-service** → **content-service** → **ai-service** (если используете контейнер) → **learning-service** → **gateway-service**.

## Запуск auth-service (минимальный сценарий: только auth + gateway)

Обязательно: **`--name auth-service`** и **`--network lms-net`**.

```bash
cd auth-service
docker run --rm --name auth-service --network lms-net -p 8081:8081 --env-file .env auth-service:local
```

- Порт **8081** проброшен на хост — для Postman/curl с машины: `http://localhost:8081`.
- Внутри сети `lms-net` этот же процесс доступен как **`auth-service:8081`**.

## Запуск gateway-service (минимальный сценарий)

В **`gateway-service/.env`** задайте (без слэша в конце):

```env
AUTH_SERVICE_URL=http://auth-service:8081
```

Остальные URL (`CONTENT_SERVICE_URL`, `LEARNING_SERVICE_URL`) оставьте заглушками, пока сервисов нет — на маршруты без живого upstream gateway может отвечать ошибкой. Для **полного стека** используйте переменные и порядок из раздела «Полный стек: PostgreSQL и все backend-сервисы в `lms-net`».

**`JWT_SECRET` должен совпадать** с тем, что в `.env` auth-service.

Запуск (в **отдельном** терминале):

```bash
cd gateway-service
docker run --rm --network lms-net -p 8080:8080 --env-file .env gateway-service:local
```

Имя контейнера gateway может быть любым; главное — та же сеть **`lms-net`**.

## Порядок и остановка

**Минимум (auth + gateway):**

1. Сеть создана (`docker network create …`).
2. Доступен PostgreSQL (хост или контейнер с согласованным `DB_URL`).
3. Запущен **auth-service** (имя `auth-service`, сеть `lms-net`).
4. Запущен **gateway-service** (сеть `lms-net`).

**Полный стек** — см. порядок в разделе «Полный стек» выше (включая **content-service**, **ai-service** при необходимости и **learning-service**).

Остановка: `Ctrl+C` в терминале с контейнером или `docker stop <container_id>`. С флагом `--rm` контейнеры удаляются после остановки.

## Проверка

| Что | URL с хоста |
|-----|-------------|
| Health auth | `GET http://localhost:8081/actuator/health` |
| Health content | `GET http://localhost:8082/actuator/health` |
| Health learning | `GET http://localhost:8083/actuator/health` |
| Health ai-service | `GET http://localhost:8084/actuator/health` |
| Health gateway | `GET http://localhost:8080/actuator/health` |
| Регистрация через gateway | `POST http://localhost:8080/api/v1/auth/register` + `Content-Type: application/json` |
| Регистрация напрямую в auth | `POST http://localhost:8081/api/v1/auth/register` |

Если через gateway снова **500** или пустой ответ, проверьте логи контейнера gateway и что в логах auth есть входящий запрос на тот же путь.

## Краткая шпаргалка

| Требование | Зачем |
|------------|--------|
| `docker network create lms-net` | Встроенный DNS имён между контейнерами |
| `--name auth-service` и т.д. | Стабильные имена хостов в `DB_URL` и `*_SERVICE_URL` |
| Все нужные контейнеры в `--network lms-net` | Один сегмент сети |
| `AUTH_SERVICE_URL=http://auth-service:8081` и аналоги для content/learning | Gateway ходит к downstream по DNS Docker |
| Одинаковый `JWT_SECRET` у auth, content, learning, gateway | Единая проверка токенов |
| `DB_URL=…//postgres:5432/…` при Postgres в той же сети | БД по имени контейнера, не `localhost` |
| `AI_SERVICE_URL=http://ai-service:8084` в learning при AI в той же сети | Learning вызывает оценку по DNS Docker |
| В ai-service: `LLM_API_BASE_URL` на хост с LLM (`host.docker.internal`) или на контейнер с API | Контейнер ai видит LLM не как `localhost` |
