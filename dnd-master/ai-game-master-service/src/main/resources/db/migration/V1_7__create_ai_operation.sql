CREATE TABLE ai_operation (
    operation_id UUID PRIMARY KEY,
    operation_key TEXT NOT NULL UNIQUE,
    payload_hash TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),
    result_json JSONB,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    lease_owner TEXT,
    lease_until TIMESTAMPTZ,
    CHECK ((lease_owner IS NULL AND lease_until IS NULL)
        OR (lease_owner IS NOT NULL AND lease_until IS NOT NULL))
);

CREATE INDEX ai_operation_worker_claim_idx
    ON ai_operation(status, lease_until) WHERE status IN ('PENDING', 'FAILED');

COMMENT ON INDEX ai_operation_worker_claim_idx IS
    'Workers claim rows with SELECT ... FOR UPDATE SKIP LOCKED';
