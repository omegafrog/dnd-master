ALTER TABLE rulebook_action_definition_extraction
    ADD COLUMN shared_rules_json JSONB NOT NULL DEFAULT '[]'::jsonb;
