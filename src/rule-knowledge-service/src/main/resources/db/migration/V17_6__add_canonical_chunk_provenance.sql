ALTER TABLE rulebook_vector_chunk
    ADD COLUMN IF NOT EXISTS canonical_path TEXT[];
ALTER TABLE rulebook_vector_chunk
    ADD COLUMN IF NOT EXISTS source_node_ids TEXT[];
ALTER TABLE rulebook_vector_chunk
    ADD COLUMN IF NOT EXISTS first_page INTEGER;
ALTER TABLE rulebook_vector_chunk
    ADD COLUMN IF NOT EXISTS last_page INTEGER;
ALTER TABLE rulebook_vector_chunk
    ADD COLUMN IF NOT EXISTS hierarchy_confidence DOUBLE PRECISION;
ALTER TABLE rulebook_vector_chunk
    ADD COLUMN IF NOT EXISTS hierarchy_resolver_version TEXT;
