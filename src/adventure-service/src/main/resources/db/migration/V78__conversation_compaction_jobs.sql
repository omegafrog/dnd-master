CREATE TABLE IF NOT EXISTS adventure_conversation_summary (
    adventure_id UUID NOT NULL REFERENCES adventure(adventure_id) ON DELETE CASCADE,
    summary_version BIGINT NOT NULL,
    source_start BIGINT NOT NULL,
    source_end BIGINT NOT NULL,
    source_adventure_version BIGINT NOT NULL,
    summary_text TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (adventure_id, summary_version),
    CHECK (source_start >= 0 AND source_end >= source_start)
);
CREATE TABLE IF NOT EXISTS adventure_conversation_compaction_job (
    job_id UUID PRIMARY KEY,
    adventure_id UUID NOT NULL REFERENCES adventure(adventure_id) ON DELETE CASCADE,
    source_start BIGINT NOT NULL,
    source_end BIGINT NOT NULL,
    expected_adventure_version BIGINT NOT NULL,
    idempotency_key TEXT NOT NULL UNIQUE,
    status TEXT NOT NULL,
    available_at TIMESTAMPTZ NOT NULL,
    lease_until TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    CHECK (source_start >= 0 AND source_end >= source_start),
    CHECK (attempts >= 0)
);
CREATE INDEX IF NOT EXISTS adventure_conversation_compaction_ready_idx
    ON adventure_conversation_compaction_job (adventure_id, status, available_at);
