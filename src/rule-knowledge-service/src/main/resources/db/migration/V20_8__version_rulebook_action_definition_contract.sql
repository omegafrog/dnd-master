ALTER TABLE rulebook_action_definition_extraction
    ADD COLUMN contract_version TEXT;

ALTER TABLE rulebook_action_definition_batch
    ADD COLUMN contract_version TEXT;
