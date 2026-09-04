ALTER TABLE adventure_runtime_turn
    ADD COLUMN IF NOT EXISTS lifecycle TEXT NOT NULL DEFAULT 'PRESENTED',
    ADD COLUMN IF NOT EXISTS fixed_resolution_json JSONB,
    ADD COLUMN IF NOT EXISTS pending_state_json JSONB,
    ADD COLUMN IF NOT EXISTS completion_proposal_json JSONB,
    ADD COLUMN IF NOT EXISTS narration TEXT NOT NULL DEFAULT '';

CREATE INDEX IF NOT EXISTS adventure_runtime_turn_lifecycle_idx
    ON adventure_runtime_turn (adventure_id, lifecycle);

CREATE UNIQUE INDEX IF NOT EXISTS adventure_runtime_turn_active_uq
    ON adventure_runtime_turn (adventure_id)
    WHERE lifecycle IN ('REQUESTED', 'RESOLVING', 'PENDING_ROLL', 'RESOLUTION_FIXED',
                       'NARRATING', 'SAFETY_CHECKING', 'READY_TO_COMMIT', 'COMMITTING');
