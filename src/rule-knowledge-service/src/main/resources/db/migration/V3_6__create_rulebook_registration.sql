CREATE TABLE IF NOT EXISTS rulebook_registration (
    rulebook_id UUID PRIMARY KEY,
    owner_player_id UUID NOT NULL,
    operation_key TEXT NOT NULL,
    content_hash TEXT NOT NULL,
    format TEXT NOT NULL,
    file_size BIGINT NOT NULL CHECK (file_size > 0),
    storage_key TEXT NOT NULL,
    processing_status TEXT NOT NULL,
    extraction_status TEXT,
    extracted_content TEXT,
    missing_locations TEXT[],
    failure_code TEXT,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS rulebook_registration_operation_key_uq
    ON rulebook_registration(operation_key);

CREATE UNIQUE INDEX IF NOT EXISTS rulebook_registration_owner_content_hash_uq
    ON rulebook_registration(owner_player_id, content_hash);

CREATE INDEX IF NOT EXISTS rulebook_registration_owner_idx
    ON rulebook_registration(owner_player_id);
