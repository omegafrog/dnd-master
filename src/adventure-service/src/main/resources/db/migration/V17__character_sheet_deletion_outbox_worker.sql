ALTER TABLE adventure_session_start_outbox
    DROP CONSTRAINT IF EXISTS adventure_session_start_outbox_status_check;

ALTER TABLE adventure_session_start_outbox
    ADD CONSTRAINT adventure_session_start_outbox_status_check
    CHECK (status IN ('PREPARED', 'COMMITTED'));

CREATE TABLE IF NOT EXISTS adventure_session_character_sheet_deletion_outbox (
    event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL,
    character_sheet_ids_json JSONB NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'FAILED', 'COMPLETED')),
    attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS adventure_session_character_sheet_deletion_pending_idx
    ON adventure_session_character_sheet_deletion_outbox(status, created_at);

CREATE OR REPLACE FUNCTION enqueue_session_character_sheet_deletion()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.status IN ('COMPLETED', 'DELETED') AND OLD.status NOT IN ('COMPLETED', 'DELETED') THEN
        INSERT INTO adventure_session_character_sheet_deletion_outbox(session_id, character_sheet_ids_json, status)
        SELECT NEW.session_id, COALESCE(jsonb_agg(character_sheet_id), '[]'::jsonb), 'PENDING'
        FROM adventure_session_party_member
        WHERE session_id = NEW.session_id;
    END IF;
    RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS adventure_session_character_sheet_deletion_trigger ON adventure_session;
CREATE TRIGGER adventure_session_character_sheet_deletion_trigger
AFTER UPDATE OF status ON adventure_session
FOR EACH ROW EXECUTE FUNCTION enqueue_session_character_sheet_deletion();
