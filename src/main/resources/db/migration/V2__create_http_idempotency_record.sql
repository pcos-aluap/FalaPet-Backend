CREATE TABLE http_idempotency_record (
    operation_scope VARCHAR(120) NOT NULL,
    subject_fingerprint CHAR(64) NOT NULL,
    key_fingerprint CHAR(64) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    state VARCHAR(16) NOT NULL,
    response_status SMALLINT,
    response_content_type VARCHAR(100),
    response_body BYTEA,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (operation_scope, subject_fingerprint, key_fingerprint),
    CONSTRAINT ck_http_idempotency_state
        CHECK (state IN ('PENDING', 'COMPLETED')),
    CONSTRAINT ck_http_idempotency_completed_response
        CHECK (
            (state = 'PENDING' AND response_status IS NULL
                AND response_content_type IS NULL AND response_body IS NULL)
            OR
            (state = 'COMPLETED' AND response_status IS NOT NULL
                AND response_content_type IS NOT NULL AND response_body IS NOT NULL)
        )
);

CREATE INDEX ix_http_idempotency_created_at
    ON http_idempotency_record (created_at);
