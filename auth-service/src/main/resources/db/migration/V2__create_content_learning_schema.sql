-- Content + Learning schema (PROJECT_DOCUMENTATION.md §4)
-- Flyway: single migration chain on shared PostgreSQL (auth-service runs migrations).

CREATE TABLE prompt_templates (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    body TEXT NOT NULL,
    temperature NUMERIC(4, 3) NOT NULL DEFAULT 0.200,
    max_tokens INT NOT NULL DEFAULT 2048,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_prompt_templates_code UNIQUE (code),
    CONSTRAINT chk_prompt_templates_temperature CHECK (temperature >= 0 AND temperature <= 2),
    CONSTRAINT chk_prompt_templates_max_tokens CHECK (max_tokens > 0 AND max_tokens <= 128000)
);

CREATE TABLE courses (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    author_id BIGINT NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_courses_author_id ON courses (author_id);

CREATE TABLE groups (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    teacher_id BIGINT NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_groups_teacher_id ON groups (teacher_id);

CREATE TABLE user_groups (
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    group_id BIGINT NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, group_id)
);

CREATE INDEX idx_user_groups_group_id ON user_groups (group_id);

CREATE TABLE group_courses (
    group_id BIGINT NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    course_id BIGINT NOT NULL REFERENCES courses (id) ON DELETE CASCADE,
    PRIMARY KEY (group_id, course_id)
);

CREATE INDEX idx_group_courses_course_id ON group_courses (course_id);

CREATE TABLE tasks (
    id BIGSERIAL PRIMARY KEY,
    course_id BIGINT NOT NULL REFERENCES courses (id) ON DELETE RESTRICT,
    type VARCHAR(32) NOT NULL,
    content JSONB NOT NULL,
    prompt_template_id BIGINT REFERENCES prompt_templates (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_tasks_type CHECK (
        type IN (
            'FILL_BLANKS',
            'TRUE_FALSE',
            'VIDEO',
            'TEXT',
            'TRANSLATION',
            'DEBATES'
        )
    )
);

CREATE INDEX idx_tasks_course_id ON tasks (course_id);
CREATE INDEX idx_tasks_prompt_template_id ON tasks (prompt_template_id);

CREATE TABLE task_results (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES tasks (id) ON DELETE CASCADE,
    student_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    answer_content JSONB NOT NULL,
    ai_feedback TEXT,
    score NUMERIC(5, 2),
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_task_results_student_task UNIQUE (student_id, task_id),
    CONSTRAINT chk_task_results_status CHECK (
        status IN ('SUBMITTED', 'CHECKED', 'VALIDATED_BY_TEACHER')
    ),
    CONSTRAINT chk_task_results_score CHECK (score IS NULL OR (score >= 0 AND score <= 100))
);

CREATE INDEX idx_task_results_task_id ON task_results (task_id);
CREATE INDEX idx_task_results_student_id ON task_results (student_id);
