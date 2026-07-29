ALTER TABLE rulebook_vector_index
    DROP CONSTRAINT IF EXISTS rulebook_vector_index_status_check;

ALTER TABLE rulebook_vector_index
    ADD CONSTRAINT rulebook_vector_index_status_check
    CHECK (status IN ('PENDING', 'EMBEDDING', 'READY', 'RETRYABLE_FAILURE', 'PERMANENT_FAILURE'));

DROP INDEX IF EXISTS rulebook_vector_worker_claim_idx;

CREATE INDEX rulebook_vector_worker_claim_idx
    ON rulebook_vector_index(status, lease_until)
    WHERE status IN ('PENDING', 'RETRYABLE_FAILURE');
