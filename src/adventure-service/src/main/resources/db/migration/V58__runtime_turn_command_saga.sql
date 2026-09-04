CREATE TABLE IF NOT EXISTS adventure_runtime_turn_command (
    command_id UUID PRIMARY KEY,
    turn_id UUID NOT NULL REFERENCES adventure_runtime_turn(turn_id) ON DELETE CASCADE,
    adventure_id UUID NOT NULL REFERENCES adventure(adventure_id) ON DELETE CASCADE,
    session_id UUID NOT NULL,
    owner_player_id UUID NOT NULL,
    target_context TEXT NOT NULL,
    command_type TEXT NOT NULL,
    payload_json TEXT NOT NULL,
    execution_status TEXT NOT NULL CHECK (execution_status IN ('PENDING', 'DONE', 'FAILED')),
    execution_order INTEGER NOT NULL CHECK (execution_order >= 0),
    idempotency_key TEXT NOT NULL UNIQUE,
    last_error TEXT NOT NULL DEFAULT '',
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    outcome_json TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (turn_id, execution_order)
);

CREATE INDEX IF NOT EXISTS adventure_runtime_turn_command_turn_idx
    ON adventure_runtime_turn_command(turn_id, execution_order);

DROP INDEX IF EXISTS adventure_runtime_turn_active_uq;
CREATE UNIQUE INDEX adventure_runtime_turn_active_uq
    ON adventure_runtime_turn (adventure_id)
    WHERE lifecycle NOT IN ('COMMITTED', 'DISCARDED', 'COMMIT_REPAIR_REQUIRED', 'PRESENTED');
