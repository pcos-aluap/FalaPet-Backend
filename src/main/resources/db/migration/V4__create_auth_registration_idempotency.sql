CREATE TABLE auth_registration_idempotency (
    subject_hash CHAR(64) NOT NULL,
    key_hash CHAR(64) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    encrypted_response BYTEA,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (subject_hash, key_hash)
);
CREATE INDEX ix_auth_registration_idempotency_created_at
    ON auth_registration_idempotency(created_at);
