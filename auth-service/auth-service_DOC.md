# Auth Service — Configuration & Runtime

This note explains **how configuration reaches the JVM**, how **Spring Boot** resolves values in `application.yml`, why **`.env` is not auto-loaded** by Java, and how that maps to **this service** (JWT, datasource).

**Step-by-step run guides** (Maven vs Docker, PowerShell snippet, `host.docker.internal`): see **[README.md](README.md)** — sections *Run without Docker* and *Run with Docker*.

---

## 1. Where configuration comes from

For a Java process, Spring Boot reads a merged **`Environment`** built from (among others):

| Source | What it is |
|--------|------------|
| **OS environment variables** | Visible to the process: `DB_URL`, `JWT_SECRET`, … Set by shell, IDE, Kubernetes, or **`docker run --env-file`**. |
| **Java system properties** | `-Dkey=value` on the `java` command line. |
| **`application.yml` / `application.properties`** | On the classpath; supports placeholders like `${VAR:default}`. |

A **`.env` file on disk is not a JVM standard**: it is only a convention (Node `dotenv`, Docker Compose, IDE plugins). Nothing reads it until **something** exports those lines into the process environment (or you add a dedicated library).

---

## 2. How `application.yml` uses environment variables

In this project, external values are wired via placeholders, for example:

```yaml
spring.datasource.url: ${DB_URL:...}
jwt.secret: ${JWT_SECRET:...}
```

Meaning: use the **environment variable** `DB_URL` / `JWT_SECRET`; if missing, use the value after `:` (default).

Spring Boot maps env names to properties using **relaxed binding** (e.g. `JWT_SECRET` works with `${JWT_SECRET}` in YAML).

**Datasource:** you do not configure `DataSource` manually in code for the common case — Spring Boot **auto-configures** it from `spring.datasource.*`, which are filled from those placeholders.

---

## 3. Local run: `mvn spring-boot:run`

Maven/Spring Boot **does not** read `.env` automatically.

- Copy `.env.example` → `.env` and fix values (see `.env.example`).
- **Before** `mvn spring-boot:run`, ensure variables exist in the **process environment** (PowerShell snippet, IDE run configuration, or manual `export`).

**Commands:** see README → **Run without Docker** (includes a PowerShell loop that loads `.env` into `Process` scope, then `mvn spring-boot:run`).

---

## 4. Docker: why it “just works” with `--env-file`

`docker run --env-file .env ...` sets **container environment variables** before `java` starts. The `java` process inherits them → Spring sees the same `Environment` as when you export vars in a shell.

**Networking note:** inside a container, `localhost` is the container itself. If PostgreSQL runs on the **host** (Windows + Docker Desktop), `DB_URL` often must use `host.docker.internal` instead of `localhost`. If Postgres is another container on the same Docker network, use that service hostname.

This repo’s `Dockerfile` only runs `java -jar`; it does **not** embed `.env` into the image. Pass secrets at run time (`--env-file`, `-e`, orchestrator secrets).

**Commands:** see README → **Run with Docker** (`docker build`, `docker run --rm -p 8081:8081 --env-file .env ...`).

---

## 5. How this maps to the auth-service code

### 5.1 JWT: YAML → `JwtProperties` → `JwtService`

`JwtProperties` binds the `jwt` prefix from configuration:

- File: `src/main/java/com/lms/auth/config/JwtProperties.java`
- Annotation: `@ConfigurationProperties(prefix = "jwt")`
- Fields: `secret`, `accessExpirySeconds`, `refreshExpirySeconds` (YAML uses kebab-case: `access-expiry-seconds`).

Registration of the properties bean:

- File: `src/main/java/com/lms/auth/AuthApplication.java`
- `@EnableConfigurationProperties(JwtProperties.class)`

`JwtService` receives `JwtProperties` via constructor and builds the HMAC key from `properties.getSecret()`:

- File: `src/main/java/com/lms/auth/security/JwtService.java`

**Chain:**

`env vars` → placeholders in `application.yml` → `jwt.*` properties → `JwtProperties` → `JwtService` (sign/verify JWT).

### 5.2 Database

`spring.datasource.*` in `application.yml` (with `${DB_URL}`, `${DB_USERNAME}`, `${DB_PASSWORD}`) → Spring Boot **DataSource** auto-configuration → JPA / Flyway.

---

## 6. Production-oriented notes

- Prefer **no default** for `JWT_SECRET` in production (fail fast if unset); defaults in YAML are risky if someone forgets env in prod.
- Secrets should come from the **platform** (Kubernetes Secrets, cloud secret manager, CI inject), not from a committed `.env`.
- `.env` belongs in **`.gitignore`**; ship **`.env.example`** with placeholders only.

---

## 7. Quick reference: useful URLs (default port)

| URL | Purpose |
|-----|---------|
| `http://localhost:8081/actuator/health` | Liveness |
| `http://localhost:8081/swagger-ui/index.html` | Try API interactively |

See `README.md` for endpoint list, admin bootstrap notes, and full **run without / with Docker** steps.
