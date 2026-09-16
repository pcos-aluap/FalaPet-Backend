CREATE TABLE mobile_device (
    id UUID PRIMARY KEY,
    tutor_id UUID NOT NULL REFERENCES auth_user(id),
    platform VARCHAR(16) NOT NULL,
    app_version VARCHAR(120) NOT NULL,
    local_playback_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    preference_version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_mobile_device_platform CHECK (platform = 'ANDROID'),
    CONSTRAINT ck_mobile_device_app_version CHECK (length(btrim(app_version)) BETWEEN 1 AND 120),
    CONSTRAINT ck_mobile_device_preference_version CHECK (preference_version > 0)
);

CREATE INDEX ix_mobile_device_tutor ON mobile_device(tutor_id, id);
