ALTER TABLE published_rag_chunk
    ADD COLUMN IF NOT EXISTS document_length INTEGER NOT NULL DEFAULT 0
        CHECK (document_length >= 0);

-- Processor chunk identifiers are only unique within their source document and extraction.
-- Rebuild legacy identities before making the global key required by BM25 term-frequency rows.
UPDATE published_rag_chunk
   SET chunk_id = md5(document_id::text || ':' || extraction_version || ':' || processor_chunk_id)::uuid
 WHERE document_length = 0;

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

-- Existing indexed extractions predate BM25 statistics. They must be reindexed before either
-- retrieval path can expose them as complete evidence.
UPDATE rag_extraction_version version
   SET status = 'FAILED', failure_reason = 'BM25_REINDEX_REQUIRED', updated_at = now()
 WHERE version.status = 'INDEXED'
   AND EXISTS (
       SELECT 1
         FROM published_rag_chunk chunk
        WHERE chunk.document_id = version.document_id
          AND chunk.extraction_version = version.extraction_version
          AND chunk.document_length = 0
   );
