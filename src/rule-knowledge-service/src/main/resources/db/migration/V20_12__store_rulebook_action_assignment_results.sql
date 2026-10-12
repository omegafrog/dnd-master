ALTER TABLE rulebook_action_definition_batch
    ADD COLUMN results_json JSONB NOT NULL DEFAULT '[]'::jsonb;
