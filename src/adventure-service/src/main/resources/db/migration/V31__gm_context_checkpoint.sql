CREATE TABLE IF NOT EXISTS gm_context_checkpoint (
    checkpoint_id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES adventure_session(session_id) ON DELETE CASCADE,
    source_turn_id UUID NOT NULL,
    checkpoint_version BIGINT NOT NULL CHECK (checkpoint_version > 0),
    summary TEXT NOT NULL,
    unresolved_threats_json TEXT NOT NULL,
    plan_revision_id UUID NOT NULL,
    plan_version BIGINT NOT NULL CHECK (plan_version > 0),
    exact_tail_json TEXT NOT NULL,
    snapshot_references_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (session_id, checkpoint_version),
    UNIQUE (session_id, source_turn_id)
);
CREATE TABLE IF NOT EXISTS gm_context_checkpoint_current (
    session_id UUID PRIMARY KEY REFERENCES adventure_session(session_id) ON DELETE CASCADE,
    checkpoint_id UUID NOT NULL REFERENCES gm_context_checkpoint(checkpoint_id),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS gm_context_checkpoint_session_idx ON gm_context_checkpoint(session_id, checkpoint_version);
