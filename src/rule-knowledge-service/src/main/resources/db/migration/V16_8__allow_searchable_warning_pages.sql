ALTER TABLE rag_extraction_page
    DROP CONSTRAINT IF EXISTS rag_extraction_page_status_check;

ALTER TABLE rag_extraction_page
    ADD CONSTRAINT rag_extraction_page_status_check
        CHECK (status IN ('VALIDATED', 'USABLE_WITH_WARNINGS', 'EXCLUDED_FORM', 'NEEDS_REVIEW'));
