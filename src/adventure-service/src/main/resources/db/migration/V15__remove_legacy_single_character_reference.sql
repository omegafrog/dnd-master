ALTER TABLE adventure DROP COLUMN IF EXISTS character_sheet_id;
ALTER TABLE adventure_runtime_binding DROP COLUMN IF EXISTS character_sheet_id;

ALTER TABLE adventure_session_start_outbox DROP CONSTRAINT IF EXISTS adventure_session_start_outbox_status_check;
UPDATE adventure_session_start_outbox SET status = 'PREPARED' WHERE status = 'PENDING';
UPDATE adventure_session_start_outbox SET status = 'COMMITTED' WHERE status = 'COMPLETED';
ALTER TABLE adventure_session_start_outbox ADD CONSTRAINT adventure_session_start_outbox_status_check
    CHECK (status IN ('PREPARED', 'COMMITTED', 'ABORTED'));
