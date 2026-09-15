CREATE TABLE pet (
    id UUID PRIMARY KEY,
    tutor_id UUID NOT NULL REFERENCES auth_user(id),
    name TEXT NOT NULL,
    species VARCHAR(8) NOT NULL,
    birth_date DATE,
    sex VARCHAR(8),
    status VARCHAR(8) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    CONSTRAINT ck_pet_name CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_pet_species CHECK (species IN ('DOG', 'CAT', 'OTHER')),
    CONSTRAINT ck_pet_sex CHECK (sex IS NULL OR sex IN ('FEMALE', 'MALE', 'UNKNOWN')),
    CONSTRAINT ck_pet_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_pet_version CHECK (version > 0)
);

CREATE INDEX ix_pet_tutor_status_order ON pet(tutor_id, status, created_at DESC, id DESC);
CREATE INDEX ix_pet_tutor_order ON pet(tutor_id, created_at DESC, id DESC);

CREATE TABLE pet_creation_idempotency (
    tutor_id UUID NOT NULL REFERENCES auth_user(id),
    key_hash CHAR(64) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    pet_id UUID NOT NULL UNIQUE REFERENCES pet(id) DEFERRABLE INITIALLY DEFERRED,
    created_name TEXT NOT NULL,
    created_species VARCHAR(8) NOT NULL,
    created_birth_date DATE,
    created_sex VARCHAR(8),
    pet_created_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tutor_id, key_hash)
);
