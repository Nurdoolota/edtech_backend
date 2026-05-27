-- V3__add_profile_and_content_hierarchy.sql
-- Auth-service Flyway: runs exactly once; all services depend on this schema.

-- ============================================================
-- 1. Users — profile fields
-- ============================================================
ALTER TABLE users ADD COLUMN avatar_url    VARCHAR(500);
ALTER TABLE users ADD COLUMN bio           VARCHAR(500);
ALTER TABLE users ADD COLUMN locale        VARCHAR(8)  NOT NULL DEFAULT 'ru';
ALTER TABLE users ADD COLUMN timezone      VARCHAR(64) NOT NULL DEFAULT 'UTC';
ALTER TABLE users ADD COLUMN email_private BOOLEAN     NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN notifications JSONB       NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE users ADD COLUMN last_login_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- ============================================================
-- 2. Courses — level, publish_mode, access_status (MAP2.md §10)
-- ============================================================
ALTER TABLE courses ADD COLUMN level         VARCHAR(8)  NOT NULL DEFAULT 'A1';
ALTER TABLE courses ADD COLUMN publish_mode  VARCHAR(16) NOT NULL DEFAULT 'AUTO';
ALTER TABLE courses ADD COLUMN access_status VARCHAR(16) NOT NULL DEFAULT 'OPEN';
ALTER TABLE courses ADD CONSTRAINT chk_courses_level
    CHECK (level IN ('A1','A2','B1','B2','C1','C2'));
ALTER TABLE courses ADD CONSTRAINT chk_courses_publish_mode
    CHECK (publish_mode IN ('AUTO','REVIEW'));
ALTER TABLE courses ADD CONSTRAINT chk_courses_access_status
    CHECK (access_status IN ('OPEN','CLOSED','PREVIEW'));

-- ============================================================
-- 3. Password reset & email change (SPEC.md §13; MAP2.md §4.2)
-- ============================================================
CREATE TABLE password_reset_codes (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash  VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    attempts   INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_password_reset_codes_user_id ON password_reset_codes(user_id);

CREATE TABLE pending_email_changes (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    new_email  VARCHAR(255) NOT NULL,
    code_hash  VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_pending_email_changes_user_id ON pending_email_changes(user_id);

-- ============================================================
-- 4. Content hierarchy: topics (MAP2.md §5.1)
-- ============================================================
CREATE TABLE topics (
    id           BIGSERIAL PRIMARY KEY,
    course_id    BIGINT NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    title        VARCHAR(500) NOT NULL,
    description  TEXT,
    order_index  INT NOT NULL DEFAULT 0,
    global_order INT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_topics_course_id ON topics(course_id);

-- ============================================================
-- 5. Lessons (MAP2.md §5.1)
-- ============================================================
CREATE TABLE lessons (
    id                  BIGSERIAL PRIMARY KEY,
    course_id           BIGINT NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    topic_id            BIGINT REFERENCES topics(id) ON DELETE CASCADE,
    order_index         INT NOT NULL DEFAULT 0,
    global_order        INT,
    title               VARCHAR(500) NOT NULL,
    status              VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    publish_mode        VARCHAR(16),
    unlock_mode         VARCHAR(16)  NOT NULL DEFAULT 'FREE',
    visible             BOOLEAN      NOT NULL DEFAULT TRUE,
    generation_metadata JSONB,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    published_at        TIMESTAMPTZ,
    CONSTRAINT chk_lessons_status  CHECK (status IN ('DRAFT','AI_GENERATED','READY','PUBLISHED','ARCHIVED')),
    CONSTRAINT chk_lessons_unlock  CHECK (unlock_mode IN ('FREE','SEQUENTIAL'))
);
CREATE INDEX idx_lessons_course_id ON lessons(course_id);
CREATE INDEX idx_lessons_topic_id  ON lessons(topic_id);

-- ============================================================
-- 6. Lesson blocks (MAP2.md §5.1)
--    content_json schemas by type (SPEC.md §5):
--    TEXT  → {"format":"markdown|html","text":"..."}
--    AUDIO → {"tts_script":"...","voice":"alloy"}
--    IMAGE → {"caption":"...","url":"..."}
--    VIDEO → {"url":"...","caption":"..."}
-- ============================================================
CREATE TABLE lesson_blocks (
    id           BIGSERIAL PRIMARY KEY,
    lesson_id    BIGINT NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
    order_index  INT NOT NULL DEFAULT 0,
    type         VARCHAR(16) NOT NULL,
    content_json JSONB NOT NULL,
    ai_generated BOOLEAN NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_blocks_type CHECK (type IN ('TEXT','AUDIO','IMAGE','VIDEO'))
);
CREATE INDEX idx_lesson_blocks_lesson_id ON lesson_blocks(lesson_id);

-- ============================================================
-- 7. Course co-teachers (MAP2.md §10)
-- ============================================================
CREATE TABLE course_teachers (
    course_id BIGINT NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    user_id   BIGINT NOT NULL REFERENCES users(id)   ON DELETE CASCADE,
    role      VARCHAR(16) NOT NULL DEFAULT 'OWNER',
    added_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (course_id, user_id),
    CONSTRAINT chk_course_teachers_role CHECK (role IN ('OWNER','EDITOR'))
);

-- ============================================================
-- 8. Lesson access grants (MAP2.md §5.1)
-- ============================================================
CREATE TABLE lesson_access (
    lesson_id  BIGINT NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
    student_id BIGINT NOT NULL REFERENCES users(id)   ON DELETE CASCADE,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (lesson_id, student_id)
);

-- ============================================================
-- 9. Tasks — add hierarchy + unlock columns (MAP2.md §5.1)
--    New task types from prototype: LISTENING, SPEAKING,
--    READING_COMPREHENSION, IMAGE_DESCRIPTION (SPEC.md §5).
--    Existing VIDEO + DEBATES kept for compatibility.
-- ============================================================
ALTER TABLE tasks ADD COLUMN lesson_id            BIGINT REFERENCES lessons(id) ON DELETE CASCADE;
ALTER TABLE tasks ADD COLUMN order_index          INT NOT NULL DEFAULT 0;
ALTER TABLE tasks ADD COLUMN title                VARCHAR(500);
ALTER TABLE tasks ADD COLUMN status               VARCHAR(32) NOT NULL DEFAULT 'DRAFT';
ALTER TABLE tasks ADD COLUMN ai_generated         BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tasks ADD COLUMN generation_metadata  JSONB;
ALTER TABLE tasks ADD COLUMN unlock_mode          VARCHAR(16) NOT NULL DEFAULT 'FREE';
ALTER TABLE tasks ADD COLUMN prerequisite_task_id BIGINT REFERENCES tasks(id) ON DELETE SET NULL;
ALTER TABLE tasks ADD COLUMN required_score       INT;

ALTER TABLE tasks DROP CONSTRAINT chk_tasks_type;
ALTER TABLE tasks ADD CONSTRAINT chk_tasks_type CHECK (type IN (
    'FILL_BLANKS','TRUE_FALSE','TEXT','TRANSLATION',
    'LISTENING','SPEAKING','READING_COMPREHENSION','IMAGE_DESCRIPTION',
    'VIDEO','DEBATES'
));
ALTER TABLE tasks ADD CONSTRAINT chk_tasks_status CHECK (status IN ('DRAFT','AI_GENERATED','READY','PUBLISHED','ARCHIVED'));
ALTER TABLE tasks ADD CONSTRAINT chk_tasks_unlock CHECK (unlock_mode IN ('FREE','SEQUENTIAL','PREREQUISITE'));
CREATE INDEX idx_tasks_lesson_id ON tasks(lesson_id);

-- ============================================================
-- 10. Task results — add breakdown / teacher columns (MAP2.md §6.1)
--     Existing score (NUMERIC 5,2) = effective score = max(teacher_score, ai_score).
--     Upon validation: teacher_score is written; score is updated to max of both.
-- ============================================================
ALTER TABLE task_results ADD COLUMN ai_score         INT;
ALTER TABLE task_results ADD COLUMN ai_breakdown     JSONB;
ALTER TABLE task_results ADD COLUMN teacher_score    INT;
ALTER TABLE task_results ADD COLUMN teacher_feedback TEXT;
ALTER TABLE task_results ADD COLUMN transcript       TEXT;
ALTER TABLE task_results ADD COLUMN media_id         VARCHAR(255);
ALTER TABLE task_results ADD CONSTRAINT chk_task_results_ai_score
    CHECK (ai_score IS NULL OR (ai_score BETWEEN 0 AND 100));
ALTER TABLE task_results ADD CONSTRAINT chk_task_results_teacher_score
    CHECK (teacher_score IS NULL OR (teacher_score BETWEEN 0 AND 100));

-- ============================================================
-- 11. AI call log (MAP2.md §7.1)
--     Table lives in auth-service Flyway (only Flyway here).
--     Written by ai-service (has its own DB connection + AiCallLog JPA entity).
--     Read via auth-service proxy: GET /api/v1/admin/ai-log → GET /internal/ai/log.
-- ============================================================
CREATE TABLE ai_call_log (
    id         BIGSERIAL PRIMARY KEY,
    request_id VARCHAR(64),
    user_id    BIGINT REFERENCES users(id) ON DELETE SET NULL,
    endpoint   VARCHAR(128) NOT NULL,
    model      VARCHAR(128) NOT NULL,
    latency_ms INT NOT NULL,
    tokens_in  INT,
    tokens_out INT,
    status     VARCHAR(16) NOT NULL,
    error      VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_ai_log_status CHECK (status IN ('SUCCESS','ERROR','TIMEOUT'))
);
CREATE INDEX idx_ai_call_log_user_id    ON ai_call_log(user_id);
CREATE INDEX idx_ai_call_log_created_at ON ai_call_log(created_at);

-- ============================================================
-- 12. Backfill: wire existing tasks into the new hierarchy
--     Creates one "Default" topic + one "Default Lesson" per course,
--     then assigns every existing task to that lesson.
--     Safe on empty databases (all INSERT/UPDATE are no-ops if no rows).
--     WHERE NOT EXISTS guards make this idempotent on partial re-run.
-- ============================================================
INSERT INTO topics (course_id, title, order_index)
    SELECT id, 'Default', 0 FROM courses
    WHERE NOT EXISTS (
        SELECT 1 FROM topics WHERE course_id = courses.id AND title = 'Default'
    );

INSERT INTO lessons (course_id, topic_id, title, status, order_index)
    SELECT c.id, t.id, 'Default Lesson', 'PUBLISHED', 0
    FROM courses c
    JOIN topics t ON t.course_id = c.id AND t.title = 'Default'
    WHERE NOT EXISTS (
        SELECT 1 FROM lessons WHERE course_id = c.id AND title = 'Default Lesson'
    );

UPDATE tasks
SET lesson_id = (
    SELECT l.id FROM lessons l
    WHERE l.course_id = tasks.course_id
    LIMIT 1
);

-- Enforce NOT NULL only after backfill; new tasks must always have lesson_id.
ALTER TABLE tasks ALTER COLUMN lesson_id SET NOT NULL;
