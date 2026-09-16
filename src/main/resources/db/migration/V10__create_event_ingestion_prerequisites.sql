CREATE TABLE esp32_device (
    id UUID PRIMARY KEY,
    tutor_id UUID NOT NULL REFERENCES auth_user(id),
    status VARCHAR(8) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_esp32_device_status CHECK (status IN ('ACTIVE', 'UNLINKED'))
);

CREATE INDEX ix_esp32_device_tutor_status ON esp32_device(tutor_id, status, id);

CREATE TABLE physical_button_binding (
    id UUID PRIMARY KEY,
    esp32_device_id UUID NOT NULL REFERENCES esp32_device(id),
    physical_button_id TEXT NOT NULL,
    button_id UUID NOT NULL REFERENCES button(id),
    status VARCHAR(8) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_physical_button_binding_status CHECK (status = 'ACTIVE'),
    CONSTRAINT ck_physical_button_binding_physical_id CHECK (length(btrim(physical_button_id)) > 0)
);

CREATE UNIQUE INDEX uq_active_physical_button_binding
    ON physical_button_binding(esp32_device_id, physical_button_id) WHERE status = 'ACTIVE';

CREATE TABLE training_session (
    id UUID PRIMARY KEY,
    tutor_id UUID NOT NULL REFERENCES auth_user(id),
    pet_id UUID NOT NULL REFERENCES pet(id),
    status VARCHAR(10) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    note TEXT,
    CONSTRAINT ck_training_session_status CHECK (status IN ('ACTIVE', 'COMPLETED')),
    CONSTRAINT ck_training_session_completion CHECK (
        (status = 'ACTIVE' AND completed_at IS NULL) OR (status = 'COMPLETED' AND completed_at IS NOT NULL)
    )
);

CREATE INDEX ix_training_session_tutor_status ON training_session(tutor_id, status, id);

CREATE TABLE button_event (
    id UUID PRIMARY KEY,
    tutor_id UUID NOT NULL REFERENCES auth_user(id),
    button_id UUID NOT NULL REFERENCES button(id),
    esp32_device_id UUID NOT NULL REFERENCES esp32_device(id),
    physical_button_id TEXT NOT NULL,
    esp_session_id UUID NOT NULL,
    sequence BIGINT NOT NULL,
    esp_uptime_ms BIGINT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    time_quality VARCHAR(32) NOT NULL,
    transport VARCHAR(8) NOT NULL,
    purpose VARCHAR(16) NOT NULL,
    training_session_id UUID REFERENCES training_session(id),
    payload_fingerprint CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_button_event_physical_id CHECK (length(btrim(physical_button_id)) > 0),
    CONSTRAINT ck_button_event_sequence CHECK (sequence >= 0),
    CONSTRAINT ck_button_event_uptime CHECK (esp_uptime_ms >= 0),
    CONSTRAINT ck_button_event_time_quality CHECK (time_quality IN ('ESTIMATED_FROM_MOBILE', 'RECEIVED_TIME_ONLY')),
    CONSTRAINT ck_button_event_transport CHECK (transport IN ('BLE', 'WIFI')),
    CONSTRAINT ck_button_event_purpose CHECK (purpose IN ('BEHAVIORAL', 'TEST'))
);

CREATE INDEX ix_button_event_tutor_occurred ON button_event(tutor_id, occurred_at DESC, id DESC);
CREATE INDEX ix_button_event_button ON button_event(button_id, occurred_at DESC, id DESC);

CREATE TABLE event_pet_attribution (
    event_id UUID PRIMARY KEY REFERENCES button_event(id),
    pet_id UUID NOT NULL REFERENCES pet(id),
    origin VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_event_pet_attribution_origin CHECK (origin = 'AUTOMATIC_SINGLE_ACTIVE_PET')
);

CREATE OR REPLACE FUNCTION prevent_button_event_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'button_event is immutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_button_event_immutable
BEFORE UPDATE OR DELETE ON button_event
FOR EACH ROW EXECUTE FUNCTION prevent_button_event_mutation();
