-- Runtime retrieval uses dense vector search only. Restore indexed versions that V19_6
-- marked unavailable solely because BM25 term statistics were absent.
UPDATE rag_extraction_version
   SET status = 'INDEXED', failure_reason = NULL, updated_at = now()
 WHERE status = 'FAILED' AND failure_reason = 'BM25_REINDEX_REQUIRED';

DROP TABLE IF EXISTS chunk_term_frequency;
ALTER TABLE published_rag_chunk DROP COLUMN IF EXISTS document_length;
