CREATE TABLE auth_user (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    email_normalized VARCHAR(254) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    CONSTRAINT uq_auth_user_email UNIQUE (email_normalized),
    CONSTRAINT ck_auth_user_email_lower CHECK (email_normalized = lower(btrim(email_normalized))),
    CONSTRAINT ck_auth_user_name CHECK (length(btrim(name)) BETWEEN 1 AND 120)
);

CREATE TABLE auth_local_credential (
    user_id UUID PRIMARY KEY REFERENCES auth_user(id),
    password_hash VARCHAR(512) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE auth_user_session (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth_user(id),
    access_token_hash CHAR(64) NOT NULL UNIQUE,
    access_expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT ck_auth_session_expiration CHECK (access_expires_at > created_at)
);
CREATE INDEX ix_auth_user_session_user ON auth_user_session(user_id);

CREATE TABLE auth_refresh_family (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL UNIQUE REFERENCES auth_user_session(id),
    created_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ
);

CREATE TABLE auth_refresh_token (
    id UUID PRIMARY KEY,
    family_id UUID NOT NULL REFERENCES auth_refresh_family(id),
    token_hash CHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    CONSTRAINT ck_auth_refresh_expiration CHECK (expires_at > created_at)
);
CREATE INDEX ix_auth_refresh_token_family ON auth_refresh_token(family_id);

CREATE TABLE auth_recovery_token (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth_user(id),
    token_hash CHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    CONSTRAINT ck_auth_recovery_expiration CHECK (expires_at > created_at)
);
CREATE INDEX ix_auth_recovery_token_user ON auth_recovery_token(user_id, consumed_at);

CREATE TABLE auth_rate_bucket (
    operation VARCHAR(32) NOT NULL,
    subject_hash CHAR(64) NOT NULL,
    window_started_at TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL CHECK (attempts > 0),
    PRIMARY KEY (operation, subject_hash)
);
CREATE INDEX ix_auth_rate_bucket_window ON auth_rate_bucket(window_started_at);
