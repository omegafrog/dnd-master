CREATE TABLE IF NOT EXISTS adventure_narrative_state (
    session_id UUID PRIMARY KEY,
    state_version BIGINT NOT NULL,
    state_json JSONB NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT adventure_narrative_state_version_nonnegative CHECK (state_version >= 0)
);
