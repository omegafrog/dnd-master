ALTER TABLE rulebook_vector_index
    ADD COLUMN IF NOT EXISTS lease_token TEXT;
