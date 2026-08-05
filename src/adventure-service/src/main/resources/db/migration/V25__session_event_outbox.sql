CREATE TABLE IF NOT EXISTS adventure_session_event_outbox (
    event_id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    version BIGINT NOT NULL CHECK (version >= 0),
    event_type TEXT NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (session_id, version)
);

CREATE INDEX IF NOT EXISTS adventure_session_event_outbox_cursor_idx
    ON adventure_session_event_outbox(session_id, version, event_id);

DELETE FROM adventure_gm_turn older
USING adventure_gm_turn newer
WHERE older.adventure_id = newer.adventure_id
  AND older.status IN ('STARTED', 'PROCESSING')
  AND newer.status IN ('STARTED', 'PROCESSING')
  AND older.turn_id < newer.turn_id;

CREATE UNIQUE INDEX IF NOT EXISTS adventure_gm_turn_one_active_idx
    ON adventure_gm_turn(adventure_id)
    WHERE status IN ('STARTED', 'PROCESSING');
