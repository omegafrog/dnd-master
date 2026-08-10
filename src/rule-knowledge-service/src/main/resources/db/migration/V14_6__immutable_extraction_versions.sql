CREATE TABLE IF NOT EXISTS knowledge_document (
    document_id UUID PRIMARY KEY,
    owner_player_id UUID NOT NULL,
    document_type TEXT NOT NULL,
    original_filename TEXT NOT NULL,
    format TEXT NOT NULL,
    file_size BIGINT NOT NULL CHECK (file_size > 0),
    content_hash TEXT NOT NULL,
    current_published_version BIGINT,
    status TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS document_processing_job (
    job_id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES knowledge_document(document_id),
    attempt INTEGER NOT NULL CHECK (attempt > 0),
    status TEXT NOT NULL,
    lease_token TEXT,
    lease_expires_at TIMESTAMPTZ,
    failure_code TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (document_id, attempt)
);

CREATE TABLE IF NOT EXISTS extraction_version (
    document_id UUID NOT NULL REFERENCES knowledge_document(document_id),
    version BIGINT NOT NULL CHECK (version > 0),
    content_hash TEXT NOT NULL,
    status TEXT NOT NULL,
    published_at TIMESTAMPTZ,
    PRIMARY KEY (document_id, version)
);

CREATE TABLE IF NOT EXISTS extraction_source_span (
    document_id UUID NOT NULL,
    version BIGINT NOT NULL,
    span_id BIGSERIAL,
    page_number INTEGER,
    left_coord DOUBLE PRECISION,
    top_coord DOUBLE PRECISION,
    right_coord DOUBLE PRECISION,
    bottom_coord DOUBLE PRECISION,
    reading_order INTEGER NOT NULL CHECK (reading_order >= 0),
    line_number INTEGER NOT NULL CHECK (line_number > 0),
    start_inclusive INTEGER NOT NULL CHECK (start_inclusive >= 0),
    end_exclusive INTEGER NOT NULL CHECK (end_exclusive >= start_inclusive),
    text TEXT NOT NULL,
    locator TEXT NOT NULL,
    PRIMARY KEY (document_id, version, span_id),
    FOREIGN KEY (document_id, version) REFERENCES extraction_version(document_id, version)
);

CREATE INDEX IF NOT EXISTS extraction_version_published_idx
    ON extraction_version(document_id, version DESC) WHERE status = 'PUBLISHED';
