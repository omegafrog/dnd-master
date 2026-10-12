ALTER TABLE published_rag_chunk
    ADD COLUMN IF NOT EXISTS content_type TEXT NOT NULL DEFAULT 'unknown',
    ADD COLUMN IF NOT EXISTS token_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS canonical_key TEXT NOT NULL DEFAULT '';

ALTER TABLE published_rag_chunk
    ADD CONSTRAINT published_rag_chunk_token_count_check CHECK (token_count >= 0);
