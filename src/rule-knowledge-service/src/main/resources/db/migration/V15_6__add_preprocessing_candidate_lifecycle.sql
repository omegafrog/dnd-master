ALTER TABLE rulebook_registration
    ADD COLUMN IF NOT EXISTS preprocessing_operation_id TEXT,
    ADD COLUMN IF NOT EXISTS candidate_extraction_version TEXT,
    ADD COLUMN IF NOT EXISTS preprocessing_policy_version TEXT,
    ADD COLUMN IF NOT EXISTS preprocessing_manifest_sha256 TEXT,
    ADD COLUMN IF NOT EXISTS preprocessing_pages TEXT NOT NULL DEFAULT '[]';
