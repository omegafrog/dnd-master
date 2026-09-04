ALTER TABLE adventure
    ADD COLUMN IF NOT EXISTS locked_scenario_package_id UUID,
    ADD COLUMN IF NOT EXISTS locked_scenario_package_revision BIGINT,
    ADD COLUMN IF NOT EXISTS game_state_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS disclosure_state_jsonb JSONB NOT NULL DEFAULT '{"disclosedFactIds":[]}'::jsonb,
    ADD COLUMN IF NOT EXISTS current_situation_id UUID,
    ADD COLUMN IF NOT EXISTS situation_revision BIGINT,
    ADD COLUMN IF NOT EXISTS current_situation_jsonb JSONB,
    ADD COLUMN IF NOT EXISTS runtime_added_facts_jsonb JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE adventure DROP CONSTRAINT IF EXISTS adventure_status_check;
ALTER TABLE adventure ADD CONSTRAINT adventure_status_check
    CHECK (status IN ('SAVED', 'STARTING', 'ACTIVE', 'COMPLETED', 'DELETED'));

ALTER TABLE adventure ADD CONSTRAINT adventure_locked_package_revision_check
    CHECK (locked_scenario_package_id IS NULL OR locked_scenario_package_revision > 0);
ALTER TABLE adventure ADD CONSTRAINT adventure_situation_revision_check
    CHECK (current_situation_id IS NULL OR situation_revision > 0);

CREATE INDEX IF NOT EXISTS adventure_locked_scenario_package_idx
    ON adventure(locked_scenario_package_id)
    WHERE locked_scenario_package_id IS NOT NULL;
