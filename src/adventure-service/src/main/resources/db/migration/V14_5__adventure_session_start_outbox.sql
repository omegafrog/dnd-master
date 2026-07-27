CREATE TABLE IF NOT EXISTS adventure_session_start_outbox (
    session_id UUID NOT NULL REFERENCES adventure_session(session_id) ON DELETE CASCADE,
    request_id UUID NOT NULL,
    adventure_id UUID NOT NULL,
    scenario_package_id UUID NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('PREPARED', 'COMMITTED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    PRIMARY KEY (session_id, request_id)
);

CREATE INDEX IF NOT EXISTS adventure_session_start_outbox_pending_idx
    ON adventure_session_start_outbox(status, created_at);

CREATE TABLE IF NOT EXISTS adventure_session_character_sheet_deletion_outbox (
    event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL,
    character_sheet_ids_json JSONB NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'FAILED', 'COMPLETED')),
    attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS adventure_session_character_sheet_deletion_pending_idx
    ON adventure_session_character_sheet_deletion_outbox(status, created_at);
