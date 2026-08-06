ALTER TABLE rulebook_vector_chunk ADD COLUMN IF NOT EXISTS visibility TEXT NOT NULL DEFAULT 'GM_ONLY';
ALTER TABLE rulebook_vector_chunk ADD COLUMN IF NOT EXISTS disclosure_event TEXT;
ALTER TABLE rulebook_vector_chunk ADD COLUMN IF NOT EXISTS disclosure_turn BIGINT NOT NULL DEFAULT 0;
ALTER TABLE rulebook_vector_chunk ADD CONSTRAINT rulebook_vector_chunk_visibility_check
    CHECK (visibility IN ('PLAYER_VISIBLE', 'GM_ONLY', 'NPC_PRIVATE', 'REVEALED_AFTER_EVENT', 'DISCOVERED', 'PUBLIC_SUMMARY'));
