CREATE TABLE IF NOT EXISTS adventure_session_event_version_counter (
    session_id UUID PRIMARY KEY,
    next_version BIGINT NOT NULL CHECK (next_version >= 0)
);

INSERT INTO adventure_session_event_version_counter(session_id, next_version)
SELECT session_id, MAX(version) + 1
FROM adventure_session_event_outbox
GROUP BY session_id
ON CONFLICT (session_id) DO UPDATE
SET next_version = GREATEST(adventure_session_event_version_counter.next_version, EXCLUDED.next_version);

ALTER TABLE adventure_session_event_outbox
    ADD CONSTRAINT adventure_session_event_outbox_session_id_version_key UNIQUE (session_id, version);
