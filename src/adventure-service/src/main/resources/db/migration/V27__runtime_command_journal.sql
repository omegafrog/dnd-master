CREATE TABLE IF NOT EXISTS adventure_runtime_command_journal (
    command_id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    turn_id UUID NOT NULL,
    owner_player_id UUID NOT NULL,
    tool_name TEXT NOT NULL,
    fingerprint TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'APPLIED', 'REJECTED', 'UNKNOWN')),
    outcome_json TEXT,
    version BIGINT NOT NULL CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS adventure_runtime_command_journal_turn_idx
    ON adventure_runtime_command_journal(session_id, turn_id, created_at);
