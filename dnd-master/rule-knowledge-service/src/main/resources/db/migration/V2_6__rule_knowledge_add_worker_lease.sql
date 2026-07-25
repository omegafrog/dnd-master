ALTER TABLE rulebook_vector_index
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    ADD COLUMN IF NOT EXISTS operation_key TEXT,
    ADD COLUMN IF NOT EXISTS lease_owner TEXT,
    ADD COLUMN IF NOT EXISTS lease_until TIMESTAMPTZ;

DO $$
BEGIN
    ALTER TABLE rulebook_vector_index
        ADD CONSTRAINT rulebook_vector_lease_pair_ck CHECK (
            (lease_owner IS NULL AND lease_until IS NULL)
            OR (lease_owner IS NOT NULL AND lease_until IS NOT NULL)
        );
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS rulebook_vector_operation_key_uq
    ON rulebook_vector_index(operation_key) WHERE operation_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS rulebook_vector_worker_claim_idx
    ON rulebook_vector_index(status, lease_until)
    WHERE status IN ('PENDING', 'FAILED');

ALTER TABLE rulebook_vector_chunk
    ADD COLUMN IF NOT EXISTS embedding_next public.vector(1536);

CREATE INDEX IF NOT EXISTS rulebook_vector_chunk_embedding_next_hnsw_idx
    ON rulebook_vector_chunk USING hnsw (embedding_next public.vector_cosine_ops)
    WHERE embedding_next IS NOT NULL;

CREATE OR REPLACE VIEW rulebook_vector_chunk_transition AS
SELECT chunk_id, index_id, rulebook_id, owner_player_id, sequence, locator, content,
       embedding AS embedding_current, embedding_next
FROM rulebook_vector_chunk;

COMMENT ON INDEX rulebook_vector_worker_claim_idx IS
    'Workers claim rows with SELECT ... FOR UPDATE SKIP LOCKED and set lease_owner/lease_until';
