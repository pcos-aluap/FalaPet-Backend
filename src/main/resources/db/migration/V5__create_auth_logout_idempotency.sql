CREATE TABLE auth_logout_idempotency (
    access_token_hash CHAR(64) NOT NULL,
    key_hash CHAR(64) NOT NULL,
    session_id UUID NOT NULL REFERENCES auth_user_session(id),
    completed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (access_token_hash, key_hash)
);
CREATE INDEX ix_auth_logout_idempotency_session ON auth_logout_idempotency(session_id);
