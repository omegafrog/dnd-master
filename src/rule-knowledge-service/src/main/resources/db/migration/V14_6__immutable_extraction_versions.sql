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
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT knowledge_document_status_ck CHECK (status IN ('REGISTERED', 'PROCESSING', 'FAILED', 'PUBLISHED'))
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
    UNIQUE (document_id, attempt),
    CONSTRAINT processing_job_status_ck CHECK (status IN ('QUEUED', 'PROCESSING', 'FAILED', 'COMPLETED'))
);

CREATE TABLE IF NOT EXISTS extraction_version (
    document_id UUID NOT NULL REFERENCES knowledge_document(document_id),
    version BIGINT NOT NULL CHECK (version > 0),
    content_hash TEXT NOT NULL,
    status TEXT NOT NULL,
    published_at TIMESTAMPTZ,
    PRIMARY KEY (document_id, version),
    CONSTRAINT extraction_version_status_ck CHECK (status IN ('DRAFT', 'VALIDATING', 'PUBLISHED', 'REJECTED'))
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

ALTER TABLE knowledge_document
    ADD CONSTRAINT knowledge_document_current_version_fk
    FOREIGN KEY (document_id, current_published_version)
    REFERENCES extraction_version(document_id, version)
    DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE extraction_source_span
    ADD CONSTRAINT extraction_span_page_ck CHECK (page_number IS NULL OR page_number > 0),
    ADD CONSTRAINT extraction_span_bounds_ck CHECK (
        left_coord IS NULL OR (top_coord IS NOT NULL AND right_coord IS NOT NULL AND bottom_coord IS NOT NULL
            AND left_coord >= 0 AND top_coord >= 0 AND right_coord >= left_coord AND bottom_coord >= top_coord)
    );

CREATE OR REPLACE FUNCTION reject_published_extraction_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' OR OLD.status = 'PUBLISHED' THEN
        RAISE EXCEPTION 'published extraction versions are immutable';
    END IF;
    RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS extraction_version_append_only ON extraction_version;
CREATE TRIGGER extraction_version_append_only
    BEFORE UPDATE OR DELETE ON extraction_version
    FOR EACH ROW EXECUTE FUNCTION reject_published_extraction_mutation();

CREATE OR REPLACE FUNCTION reject_published_span_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM extraction_version v WHERE v.document_id = OLD.document_id
        AND v.version = OLD.version AND v.status = 'PUBLISHED') THEN
        RAISE EXCEPTION 'source spans of published extraction versions are immutable';
    END IF;
    RETURN COALESCE(NEW, OLD);
END $$;

DROP TRIGGER IF EXISTS extraction_span_append_only ON extraction_source_span;
CREATE TRIGGER extraction_span_append_only
    BEFORE UPDATE OR DELETE ON extraction_source_span
    FOR EACH ROW EXECUTE FUNCTION reject_published_span_mutation();

CREATE INDEX IF NOT EXISTS extraction_version_published_idx
    ON extraction_version(document_id, version DESC) WHERE status = 'PUBLISHED';
