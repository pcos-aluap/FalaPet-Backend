CREATE TABLE user_deletion_request (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES auth_user(id),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    requested_at TIMESTAMPTZ NOT NULL,
    effective_at TIMESTAMPTZ,
    CONSTRAINT ck_user_deletion_request_pending CHECK (status = 'PENDING' AND effective_at IS NULL)
);

CREATE INDEX ix_user_deletion_request_requested_at ON user_deletion_request(requested_at);
