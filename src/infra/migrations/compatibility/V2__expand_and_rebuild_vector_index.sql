-- Expand only: every new column is nullable so the previous application remains writable.
ALTER TABLE compat_adventure ADD COLUMN context_json JSONB;
ALTER TABLE compat_rulebook_source ADD COLUMN content_hash TEXT;

CREATE TABLE compat_rulebook_vector_v2 (
    chunk_id UUID PRIMARY KEY REFERENCES compat_rulebook_source(chunk_id) ON DELETE CASCADE,
    embedding vector(4) NOT NULL,
    rebuilt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Deterministic compatibility rebuild fixture. Production rebuilders derive the vector from stored source content.
INSERT INTO compat_rulebook_vector_v2 (chunk_id, embedding)
SELECT chunk_id, CAST('[1,0,0,0]' AS vector)
FROM compat_rulebook_source
ON CONFLICT (chunk_id) DO NOTHING;

CREATE INDEX compat_rulebook_vector_v2_cosine_idx
    ON compat_rulebook_vector_v2 USING hnsw (embedding vector_cosine_ops);

CREATE VIEW compat_rulebook_search_current AS
SELECT source.chunk_id, source.owner_player_id, source.rulebook_id, source.locator,
       source.content, vectors.embedding
FROM compat_rulebook_source source
JOIN compat_rulebook_vector_v2 vectors USING (chunk_id);
