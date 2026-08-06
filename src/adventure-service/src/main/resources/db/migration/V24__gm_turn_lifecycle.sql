CREATE TABLE IF NOT EXISTS adventure_gm_turn (
    turn_id UUID PRIMARY KEY,
    command_id UUID NOT NULL UNIQUE,
    adventure_id UUID NOT NULL REFERENCES adventure(adventure_id) ON DELETE CASCADE,
    expected_session_version BIGINT NOT NULL CHECK (expected_session_version >= 0),
    input_type TEXT NOT NULL CHECK (input_type IN ('TEXT', 'MAP_ACTION', 'META_QUESTION')),
    input_json TEXT NOT NULL,
    fingerprint TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('STARTED', 'PROCESSING', 'COMMITTED', 'FAILED')),
    failure TEXT,
    provider_metadata TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS adventure_gm_turn_adventure_idx
    ON adventure_gm_turn (adventure_id, created_at, turn_id);
