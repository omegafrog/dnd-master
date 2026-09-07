ALTER TABLE published_rag_chunk
    ADD COLUMN IF NOT EXISTS parent_key TEXT;

CREATE INDEX IF NOT EXISTS published_rag_chunk_parent_sequence_idx
    ON published_rag_chunk (document_id, extraction_version, parent_key, sequence);
