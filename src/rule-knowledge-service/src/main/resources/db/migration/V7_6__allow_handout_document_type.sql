ALTER TABLE rulebook_registration DROP CONSTRAINT IF EXISTS rulebook_registration_document_type_ck;
ALTER TABLE rulebook_registration
    ADD CONSTRAINT rulebook_registration_document_type_ck
    CHECK (document_type IN ('RULEBOOK', 'STORYBOOK', 'HANDOUT'));
