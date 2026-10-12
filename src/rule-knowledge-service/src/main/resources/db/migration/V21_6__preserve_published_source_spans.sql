ALTER TABLE published_rag_chunk
    ADD COLUMN IF NOT EXISTS source_spans JSONB NOT NULL DEFAULT '[]'::jsonb;
