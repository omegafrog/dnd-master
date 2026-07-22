ALTER TABLE rulebook_registration
    ADD COLUMN document_type TEXT NOT NULL DEFAULT 'RULEBOOK',
    ADD COLUMN original_filename TEXT NOT NULL DEFAULT 'legacy-rulebook';

ALTER TABLE rulebook_registration
    ADD CONSTRAINT rulebook_registration_document_type_ck
    CHECK (document_type IN ('RULEBOOK', 'STORYBOOK'));
