ALTER TABLE scenario_package
    ADD COLUMN IF NOT EXISTS spell_definitions_json JSONB;
