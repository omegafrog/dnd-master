CREATE TABLE rulebook_action_definition_extraction (
    document_id UUID NOT NULL,
    extraction_version TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED')),
    preprocessed_page_count INTEGER NOT NULL DEFAULT 0,
    indexed_page_count INTEGER NOT NULL DEFAULT 0,
    scanned_chunk_count INTEGER NOT NULL DEFAULT 0,
    scanned_section_count INTEGER NOT NULL DEFAULT 0,
    definitions_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    failure_code TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (document_id, extraction_version)
);
CREATE TABLE rulebook_action_definition_batch (
    document_id UUID NOT NULL,
    extraction_version TEXT NOT NULL,
    batch_index INTEGER NOT NULL,
    source_chunk_ids JSONB NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('COMPLETED', 'FAILED')),
    definitions_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    failure_code TEXT,
    PRIMARY KEY (document_id, extraction_version, batch_index),
    FOREIGN KEY (document_id, extraction_version)
        REFERENCES rulebook_action_definition_extraction(document_id, extraction_version) ON DELETE CASCADE
);
