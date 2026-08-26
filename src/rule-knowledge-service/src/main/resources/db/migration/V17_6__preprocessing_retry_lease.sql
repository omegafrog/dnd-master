CREATE TABLE IF NOT EXISTS rag_extraction_retry (
    document_id UUID NOT NULL REFERENCES rulebook_registration(rulebook_id) ON DELETE CASCADE,
    request_id TEXT NOT NULL,
    candidate_version TEXT NOT NULL,
    pages INTEGER[] NOT NULL,
    lease_token TEXT NOT NULL,
    lease_until TIMESTAMPTZ NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('LEASED', 'COMPLETED')),
    result_version TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (document_id, request_id)
);

CREATE INDEX IF NOT EXISTS rag_extraction_retry_lease_idx
    ON rag_extraction_retry (document_id, lease_until);
