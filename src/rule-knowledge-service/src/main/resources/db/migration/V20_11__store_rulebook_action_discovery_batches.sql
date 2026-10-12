ALTER TABLE rulebook_action_definition_batch
    ADD COLUMN candidates_json JSONB NOT NULL DEFAULT '[]'::jsonb;
