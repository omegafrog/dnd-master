ALTER TABLE rulebook_action_definition_extraction
    ADD COLUMN query_coverage_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN evidence_limit_reached BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN candidate_evidence_count INTEGER NOT NULL DEFAULT 0;
