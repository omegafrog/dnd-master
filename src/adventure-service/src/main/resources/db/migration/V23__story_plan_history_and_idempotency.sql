ALTER TABLE scenario_compilation ADD COLUMN IF NOT EXISTS idempotency_key TEXT NOT NULL DEFAULT '';
UPDATE scenario_compilation SET idempotency_key = input_fingerprint WHERE idempotency_key = '';
CREATE UNIQUE INDEX IF NOT EXISTS scenario_compilation_idempotency_idx ON scenario_compilation(idempotency_key);

CREATE TABLE IF NOT EXISTS adventure_story_plan_history (
    history_id UUID PRIMARY KEY,
    plan_id UUID NOT NULL,
    session_id UUID NOT NULL,
    package_revision BIGINT NOT NULL,
    party_revision BIGINT NOT NULL,
    plan_version BIGINT NOT NULL,
    status TEXT NOT NULL,
    current_stage INTEGER NOT NULL,
    stages_json TEXT NOT NULL,
    failure_reason TEXT,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS adventure_story_plan_history_session_idx ON adventure_story_plan_history(session_id, plan_version DESC);
