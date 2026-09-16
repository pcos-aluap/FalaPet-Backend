ALTER TABLE event_pet_attribution ADD COLUMN version BIGINT NOT NULL DEFAULT 1;
ALTER TABLE event_pet_attribution DROP CONSTRAINT ck_event_pet_attribution_origin;
ALTER TABLE event_pet_attribution ADD CONSTRAINT ck_event_pet_attribution_origin
    CHECK (origin IN ('AUTOMATIC_SINGLE_ACTIVE_PET', 'TUTOR'));

CREATE TABLE event_button_snapshot (
    event_id UUID PRIMARY KEY REFERENCES button_event(id),
    button_name VARCHAR(120) NOT NULL
);
INSERT INTO event_button_snapshot(event_id, button_name)
SELECT event.id, button.name FROM button_event event JOIN button ON button.id = event.button_id;

CREATE TABLE event_user_classification (
    event_id UUID PRIMARY KEY REFERENCES button_event(id),
    value VARCHAR(32) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT ck_event_user_classification_value CHECK (value IN ('APPARENTLY_INTENTIONAL', 'APPARENTLY_ACCIDENTAL', 'UNCERTAIN'))
);

CREATE TABLE context_type (
    id UUID PRIMARY KEY,
    tutor_id UUID NOT NULL REFERENCES auth_user(id),
    name VARCHAR(80) NOT NULL,
    status VARCHAR(8) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT ck_context_type_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_context_type_name CHECK (length(btrim(name)) BETWEEN 1 AND 80)
);
CREATE INDEX ix_context_type_tutor_status_created ON context_type(tutor_id, status, created_at DESC, id DESC);

CREATE TABLE event_context (
    event_id UUID PRIMARY KEY REFERENCES button_event(id),
    context_type_id UUID REFERENCES context_type(id),
    context_type_name_snapshot VARCHAR(80),
    note TEXT,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT ck_event_context_content CHECK (context_type_id IS NOT NULL OR note IS NOT NULL),
    CONSTRAINT ck_event_context_note CHECK (note IS NULL OR length(note) <= 1000),
    CONSTRAINT ck_event_context_snapshot CHECK ((context_type_id IS NULL AND context_type_name_snapshot IS NULL) OR (context_type_id IS NOT NULL AND context_type_name_snapshot IS NOT NULL))
);
CREATE INDEX ix_button_event_tutor_history ON button_event(tutor_id, occurred_at DESC, id DESC);
CREATE INDEX ix_event_pet_attribution_pet ON event_pet_attribution(pet_id, event_id);
CREATE INDEX ix_event_user_classification_value ON event_user_classification(value, event_id);
