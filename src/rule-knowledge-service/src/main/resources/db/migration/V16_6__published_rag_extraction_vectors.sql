ALTER TABLE rulebook_registration
    ADD COLUMN IF NOT EXISTS published_extraction_version TEXT;

CREATE TABLE IF NOT EXISTS rag_extraction_version (
    document_id UUID NOT NULL REFERENCES rulebook_registration(rulebook_id) ON DELETE CASCADE,
    extraction_version TEXT NOT NULL,
    owner_player_id UUID NOT NULL,
    operation_id TEXT NOT NULL,
    source_hash TEXT NOT NULL,
    policy_version TEXT NOT NULL,
    manifest_hash TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('PROCESSING', 'NEEDS_REVIEW', 'VALIDATED', 'INDEXING', 'INDEXED', 'FAILED')),
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (document_id, extraction_version),
    UNIQUE (operation_id)
);

CREATE INDEX IF NOT EXISTS rag_extraction_version_document_status_idx
    ON rag_extraction_version (document_id, status, updated_at DESC);

CREATE TABLE IF NOT EXISTS rag_extraction_page (
    document_id UUID NOT NULL,
    extraction_version TEXT NOT NULL,
    page_number INTEGER NOT NULL CHECK (page_number > 0),
    status TEXT NOT NULL CHECK (status IN ('VALIDATED', 'NEEDS_REVIEW')),
    attempts INTEGER NOT NULL CHECK (attempts > 0),
    findings TEXT[] NOT NULL DEFAULT '{}',
    PRIMARY KEY (document_id, extraction_version, page_number),
    FOREIGN KEY (document_id, extraction_version)
        REFERENCES rag_extraction_version(document_id, extraction_version) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS published_rag_chunk (
    document_id UUID NOT NULL,
    owner_player_id UUID NOT NULL,
    extraction_version TEXT NOT NULL,
    processor_chunk_id TEXT NOT NULL,
    chunk_id UUID NOT NULL,
    sequence INTEGER NOT NULL CHECK (sequence >= 0),
    content TEXT NOT NULL,
    embedding_text TEXT NOT NULL,
    embedding public.vector NOT NULL,
    embedding_model TEXT NOT NULL,
    embedding_dimension INTEGER NOT NULL CHECK (embedding_dimension > 0),
    section_path TEXT[] NOT NULL DEFAULT '{}',
    page_number INTEGER NOT NULL CHECK (page_number > 0),
    bbox DOUBLE PRECISION[],
    table_cell TEXT,
    original_locator TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (document_id, extraction_version, processor_chunk_id),
    UNIQUE (document_id, extraction_version, chunk_id),
    FOREIGN KEY (document_id, extraction_version)
        REFERENCES rag_extraction_version(document_id, extraction_version) ON DELETE CASCADE,
    FOREIGN KEY (document_id, extraction_version, page_number)
        REFERENCES rag_extraction_page(document_id, extraction_version, page_number)
);

CREATE INDEX IF NOT EXISTS published_rag_chunk_public_lookup_idx
    ON published_rag_chunk (owner_player_id, document_id, extraction_version, page_number, sequence);
