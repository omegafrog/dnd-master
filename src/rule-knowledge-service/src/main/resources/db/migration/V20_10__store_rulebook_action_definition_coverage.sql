ALTER TABLE rulebook_action_definition_extraction
    ADD COLUMN action_coverage_json JSONB NOT NULL DEFAULT '[]'::jsonb;
