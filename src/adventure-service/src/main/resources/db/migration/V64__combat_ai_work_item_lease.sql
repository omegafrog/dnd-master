CREATE TABLE IF NOT EXISTS combat_work_item (
    work_item_id UUID PRIMARY KEY,
    encounter_id UUID NOT NULL REFERENCES combat_encounter(encounter_id) ON DELETE CASCADE,
    operation_id UUID,
    expected_encounter_version BIGINT NOT NULL CHECK (expected_encounter_version >= 0),
    work_type TEXT NOT NULL CHECK (work_type IN ('AI_TURN')),
    due_at TIMESTAMPTZ NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'CLAIMED', 'COMPLETED', 'FAILED')),
    lease_token UUID,
    worker_id TEXT,
    lease_until TIMESTAMPTZ,
    failure TEXT,
    tactical_instruction TEXT NOT NULL,
    tactical_constraints JSONB NOT NULL DEFAULT '[]'::jsonb,
    command_json JSONB,
    completed_steps INT NOT NULL DEFAULT 0 CHECK (completed_steps >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS combat_work_item_due_idx ON combat_work_item(status, due_at);
CREATE UNIQUE INDEX IF NOT EXISTS combat_work_item_operation_uq ON combat_work_item(operation_id) WHERE operation_id IS NOT NULL;
