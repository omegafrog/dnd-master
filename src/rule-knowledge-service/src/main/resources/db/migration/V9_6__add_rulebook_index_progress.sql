ALTER TABLE rulebook_vector_index
    ADD COLUMN IF NOT EXISTS total_chunks INTEGER NOT NULL DEFAULT 0 CHECK (total_chunks >= 0),
    ADD COLUMN IF NOT EXISTS completed_chunks INTEGER NOT NULL DEFAULT 0 CHECK (completed_chunks >= 0),
    ADD COLUMN IF NOT EXISTS next_chunk_sequence INTEGER NOT NULL DEFAULT 0 CHECK (next_chunk_sequence >= 0),
    ADD COLUMN IF NOT EXISTS last_progress_at TIMESTAMPTZ;
