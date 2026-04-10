CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(32) NOT NULL UNIQUE,
    CONSTRAINT chk_roles_name CHECK (name IN ('STUDENT', 'TEACHER', 'ADMIN'))
);

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    role_id BIGINT NOT NULL REFERENCES roles (id) ON DELETE RESTRICT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE INDEX idx_users_role_id ON users (role_id);

CREATE TABLE impersonation_audit (
    id BIGSERIAL PRIMARY KEY,
    admin_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    target_user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    ip_address VARCHAR(45)
);

CREATE INDEX idx_impersonation_admin_id ON impersonation_audit (admin_id);
CREATE INDEX idx_impersonation_target_user_id ON impersonation_audit (target_user_id);
CREATE INDEX idx_impersonation_open ON impersonation_audit (admin_id, target_user_id) WHERE ended_at IS NULL;

INSERT INTO roles (name) VALUES ('STUDENT'), ('TEACHER'), ('ADMIN');
