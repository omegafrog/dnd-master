ALTER TABLE gm_provider_binding
    ADD COLUMN IF NOT EXISTS requested_endpoint_id UUID;

ALTER TABLE adventure_gm_turn
    ADD COLUMN IF NOT EXISTS requested_endpoint_id UUID,
    ADD COLUMN IF NOT EXISTS requested_provider TEXT,
    ADD COLUMN IF NOT EXISTS requested_model TEXT,
    ADD COLUMN IF NOT EXISTS requested_reasoning TEXT,
    ADD COLUMN IF NOT EXISTS effective_endpoint_id UUID,
    ADD COLUMN IF NOT EXISTS effective_endpoint_version TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS effective_provider TEXT,
    ADD COLUMN IF NOT EXISTS effective_model TEXT,
    ADD COLUMN IF NOT EXISTS effective_reasoning TEXT,
    ADD COLUMN IF NOT EXISTS attempt_count INTEGER NOT NULL DEFAULT 1;

ALTER TABLE adventure_runtime_turn
    ADD COLUMN IF NOT EXISTS requested_endpoint_id UUID,
    ADD COLUMN IF NOT EXISTS requested_provider TEXT,
    ADD COLUMN IF NOT EXISTS requested_model TEXT,
    ADD COLUMN IF NOT EXISTS requested_reasoning TEXT,
    ADD COLUMN IF NOT EXISTS effective_endpoint_id UUID,
    ADD COLUMN IF NOT EXISTS effective_endpoint_version TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS effective_provider TEXT,
    ADD COLUMN IF NOT EXISTS effective_model TEXT,
    ADD COLUMN IF NOT EXISTS effective_reasoning TEXT,
    ADD COLUMN IF NOT EXISTS attempt_count INTEGER NOT NULL DEFAULT 1;

COMMENT ON COLUMN adventure_gm_turn.requested_provider IS 'Requested selection; null means legacy row.';
COMMENT ON COLUMN adventure_gm_turn.effective_provider IS 'Actual immutable invocation selection; null means legacy row.';
