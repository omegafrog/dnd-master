ALTER TABLE rulebook_vector_chunk
    DROP CONSTRAINT IF EXISTS rulebook_vector_chunk_pkey;

ALTER TABLE rulebook_vector_chunk
    ADD CONSTRAINT rulebook_vector_chunk_pkey PRIMARY KEY (index_id, chunk_id);
