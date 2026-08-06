CREATE TABLE IF NOT EXISTS gm_provider_binding (
    session_id UUID PRIMARY KEY,
    provider TEXT NOT NULL,
    model TEXT NOT NULL,
    reasoning TEXT NOT NULL,
    state_version BIGINT NOT NULL CHECK (state_version >= 0),
    turn_in_progress BOOLEAN NOT NULL DEFAULT FALSE
);
