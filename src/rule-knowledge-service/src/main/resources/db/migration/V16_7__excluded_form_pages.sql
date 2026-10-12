ALTER TABLE rag_extraction_page
    DROP CONSTRAINT IF EXISTS rag_extraction_page_status_check;

ALTER TABLE rag_extraction_page
    ADD CONSTRAINT rag_extraction_page_status_check
        CHECK (status IN ('VALIDATED', 'EXCLUDED_FORM', 'NEEDS_REVIEW'));

ALTER TABLE rag_extraction_page
    ADD COLUMN IF NOT EXISTS page_classification TEXT,
    ADD COLUMN IF NOT EXISTS classification_evidence JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE rag_extraction_page
    ADD CONSTRAINT rag_extraction_page_form_evidence_check
        CHECK (status <> 'EXCLUDED_FORM' OR
               (page_classification = 'form' AND classification_evidence <> '{}'::jsonb));
