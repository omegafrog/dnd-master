ALTER TABLE adventure_runtime_command_journal
    ADD COLUMN IF NOT EXISTS candidate_id UUID,
    ADD COLUMN IF NOT EXISTS tool_index INTEGER,
    ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS failure_code TEXT;

CREATE INDEX IF NOT EXISTS adventure_runtime_command_journal_pending_idx
    ON adventure_runtime_command_journal(status, updated_at)
    WHERE status = 'PENDING';
