ALTER TABLE scenario_package
    ADD COLUMN IF NOT EXISTS character_limit INT NOT NULL DEFAULT 1 CHECK (character_limit >= 1),
    ADD COLUMN IF NOT EXISTS character_limit_source_document_id UUID,
    ADD COLUMN IF NOT EXISTS character_limit_source_extraction_version BIGINT,
    ADD COLUMN IF NOT EXISTS character_limit_source_locator TEXT,
    ADD COLUMN IF NOT EXISTS character_limit_source_quote TEXT NOT NULL DEFAULT '';
