CREATE TABLE IF NOT EXISTS adventure_session_event_version_counter (
    session_id UUID PRIMARY KEY,
    next_version BIGINT NOT NULL CHECK (next_version >= 0)
);

-- V26 temporarily allowed different event types to share a session/version.
-- Reassign legacy rows deterministically before restoring atomic uniqueness.
WITH session_offsets AS (
    SELECT session_id, MAX(version) + COUNT(*) + 1 AS offset_value
    FROM adventure_session_event_outbox
    GROUP BY session_id
)
UPDATE adventure_session_event_outbox event
SET version = event.version + offsets.offset_value
FROM session_offsets offsets
WHERE event.session_id = offsets.session_id;

WITH ordered_events AS (
    SELECT event_id, ROW_NUMBER() OVER (PARTITION BY session_id ORDER BY version, event_id) - 1 AS version
    FROM adventure_session_event_outbox
)
UPDATE adventure_session_event_outbox event
SET version = ordered.version
FROM ordered_events ordered
WHERE event.event_id = ordered.event_id;

INSERT INTO adventure_session_event_version_counter(session_id, next_version)
SELECT session_id, MAX(version) + 1
FROM adventure_session_event_outbox
GROUP BY session_id
ON CONFLICT (session_id) DO UPDATE
SET next_version = GREATEST(adventure_session_event_version_counter.next_version, EXCLUDED.next_version);

ALTER TABLE adventure_session_event_outbox
    ADD CONSTRAINT adventure_session_event_outbox_session_id_version_key UNIQUE (session_id, version);
