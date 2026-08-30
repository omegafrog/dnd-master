CREATE TABLE IF NOT EXISTS runtime_turn_failure_artifact (
    artifact_id UUID PRIMARY KEY,
    turn_id UUID NOT NULL,
    failure_code TEXT NOT NULL,
    stage TEXT NOT NULL,
    retryable BOOLEAN NOT NULL,
    root_cause_class TEXT NOT NULL,
    correlation_id UUID NOT NULL,
    attempt INT NOT NULL CHECK (attempt >= 1),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS runtime_turn_failure_artifact_turn_idx
    ON runtime_turn_failure_artifact(turn_id, occurred_at);
