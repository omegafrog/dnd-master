ALTER TABLE rulebook_registration
    ADD COLUMN IF NOT EXISTS document_type TEXT NOT NULL DEFAULT 'RULEBOOK',
    ADD COLUMN IF NOT EXISTS original_filename TEXT NOT NULL DEFAULT 'legacy-rulebook';

DO $$
BEGIN
    ALTER TABLE rulebook_registration
        ADD CONSTRAINT rulebook_registration_document_type_ck
        CHECK (document_type IN ('RULEBOOK', 'STORYBOOK'));
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;
