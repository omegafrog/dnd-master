ALTER TABLE scenario_package ADD COLUMN IF NOT EXISTS scenario_model_json JSONB;

ALTER TABLE scenario_compilation ADD COLUMN IF NOT EXISTS primary_storybook_id UUID;
ALTER TABLE scenario_compilation ADD COLUMN IF NOT EXISTS integration_prompt TEXT NOT NULL DEFAULT '';
ALTER TABLE scenario_compilation ADD COLUMN IF NOT EXISTS creativity TEXT NOT NULL DEFAULT 'CONSERVATIVE';
ALTER TABLE scenario_compilation ADD COLUMN IF NOT EXISTS input_snapshot_json JSONB;
ALTER TABLE scenario_compilation ADD COLUMN IF NOT EXISTS diagnostics JSONB NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE scenario_compilation ADD COLUMN IF NOT EXISTS processing_started_at TIMESTAMPTZ;
ALTER TABLE scenario_compilation ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS scenario_compilation_outbox (
    message_id UUID PRIMARY KEY,
    compilation_id UUID NOT NULL REFERENCES scenario_compilation(compilation_id) ON DELETE CASCADE,
    message_type TEXT NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivered_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS scenario_compilation_outbox_identity_idx
    ON scenario_compilation_outbox(compilation_id, message_type);
