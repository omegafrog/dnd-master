CREATE TABLE IF NOT EXISTS adventure_session_start_outbox (
    session_id UUID NOT NULL REFERENCES adventure_session(session_id) ON DELETE CASCADE,
    request_id UUID NOT NULL,
    adventure_id UUID NOT NULL,
    scenario_package_id UUID NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'COMPLETED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    PRIMARY KEY (session_id, request_id)
);

CREATE INDEX IF NOT EXISTS adventure_session_start_outbox_pending_idx
    ON adventure_session_start_outbox(status, created_at);
