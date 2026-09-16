CREATE TABLE button (
    id UUID PRIMARY KEY,
    tutor_id UUID NOT NULL REFERENCES auth_user(id),
    name TEXT NOT NULL,
    description TEXT,
    status VARCHAR(8) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    CONSTRAINT ck_button_name CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_button_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_button_version CHECK (version > 0)
);

CREATE INDEX ix_button_tutor_status_order ON button(tutor_id, status, created_at DESC, id DESC);
CREATE INDEX ix_button_tutor_order ON button(tutor_id, created_at DESC, id DESC);

CREATE TABLE button_creation_idempotency (
    tutor_id UUID NOT NULL REFERENCES auth_user(id),
    key_hash CHAR(64) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    button_id UUID NOT NULL UNIQUE REFERENCES button(id) DEFERRABLE INITIALLY DEFERRED,
    created_name TEXT NOT NULL,
    created_description TEXT,
    button_created_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tutor_id, key_hash)
);
