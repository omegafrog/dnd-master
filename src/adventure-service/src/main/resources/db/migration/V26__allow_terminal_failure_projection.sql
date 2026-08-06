ALTER TABLE adventure_session_event_outbox
    DROP CONSTRAINT IF EXISTS adventure_session_event_outbox_session_id_version_key;

CREATE UNIQUE INDEX IF NOT EXISTS adventure_session_event_outbox_session_version_type_uq
    ON adventure_session_event_outbox(session_id, version, event_type);
