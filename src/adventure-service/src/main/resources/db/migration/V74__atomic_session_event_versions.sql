CREATE TABLE IF NOT EXISTS adventure_session_event_version_counter (
    session_id UUID PRIMARY KEY,
    next_version BIGINT NOT NULL CHECK (next_version >= 0)
);

-- V26 temporarily allowed different event types to share a session/version.
-- Keep every non-conflicting historical version unchanged. For a duplicate,
-- retain the deterministic first event at its established cursor and append
-- the remaining events after the session's existing history. This preserves
-- established replay cursors and gives legacy duplicates a stable order.
WITH duplicate_rows AS (
    SELECT event_id, session_id, version,
           ROW_NUMBER() OVER (PARTITION BY session_id, version ORDER BY event_id) AS conflict_order
    FROM adventure_session_event_outbox
    WHERE (session_id, version) IN (
        SELECT session_id, version
        FROM adventure_session_event_outbox
        GROUP BY session_id, version
        HAVING COUNT(*) > 1
    )
), legacy_duplicates AS (
    SELECT event_id, session_id,
           ROW_NUMBER() OVER (PARTITION BY session_id ORDER BY version, event_id) AS appended_order
    FROM duplicate_rows
    WHERE conflict_order > 1
), session_maximums AS (
    SELECT session_id, MAX(version) AS maximum_version
    FROM adventure_session_event_outbox
    GROUP BY session_id
)
UPDATE adventure_session_event_outbox event
SET version = maximums.maximum_version + duplicates.appended_order
FROM legacy_duplicates duplicates
JOIN session_maximums maximums ON maximums.session_id = duplicates.session_id
WHERE event.event_id = duplicates.event_id;

INSERT INTO adventure_session_event_version_counter(session_id, next_version)
SELECT session_id, MAX(version) + 1
FROM adventure_session_event_outbox
GROUP BY session_id
ON CONFLICT (session_id) DO UPDATE
SET next_version = GREATEST(adventure_session_event_version_counter.next_version, EXCLUDED.next_version);

ALTER TABLE adventure_session_event_outbox
    ADD CONSTRAINT adventure_session_event_outbox_session_id_version_key UNIQUE (session_id, version);
