ALTER TABLE adventure_session
    ADD COLUMN IF NOT EXISTS runtime_scenario_id UUID,
    ADD COLUMN IF NOT EXISTS runtime_rule_set_id UUID,
    ADD COLUMN IF NOT EXISTS runtime_rulebook_ids_json TEXT,
    ADD COLUMN IF NOT EXISTS runtime_engine_id TEXT,
    ADD COLUMN IF NOT EXISTS runtime_tool_ids_json TEXT,
    ADD COLUMN IF NOT EXISTS runtime_initial_scene TEXT;

ALTER TABLE adventure_session
    ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'DRAFT',
    ADD COLUMN IF NOT EXISTS started_adventure_id UUID,
    ADD COLUMN IF NOT EXISTS start_request_id UUID;
