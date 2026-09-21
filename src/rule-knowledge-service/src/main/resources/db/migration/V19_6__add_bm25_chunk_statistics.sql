ALTER TABLE published_rag_chunk
    ADD COLUMN IF NOT EXISTS document_length INTEGER NOT NULL DEFAULT 0
        CHECK (document_length >= 0);

ALTER TABLE published_rag_chunk
    ADD CONSTRAINT published_rag_chunk_chunk_id_key UNIQUE (chunk_id);

CREATE TABLE IF NOT EXISTS chunk_term_frequency (
    chunk_id UUID NOT NULL REFERENCES published_rag_chunk(chunk_id) ON DELETE CASCADE,
    term TEXT NOT NULL,
    term_frequency INTEGER NOT NULL CHECK (term_frequency > 0),
    PRIMARY KEY (chunk_id, term)
);

CREATE INDEX IF NOT EXISTS chunk_term_frequency_term_idx
    ON chunk_term_frequency (term);

CREATE INDEX IF NOT EXISTS chunk_term_frequency_chunk_id_idx
    ON chunk_term_frequency (chunk_id);
