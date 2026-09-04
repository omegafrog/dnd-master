CREATE TABLE IF NOT EXISTS rag_retrieval_trace (
    retrieval_trace_id BIGSERIAL PRIMARY KEY,
    operation_id TEXT NOT NULL,
    phase TEXT NOT NULL,
    tool_name TEXT NOT NULL,
    call_index INTEGER NOT NULL CHECK (call_index > 0),
    query TEXT NOT NULL,
    request_json JSONB NOT NULL,
    raw_response_json JSONB NOT NULL,
    projected_response_json JSONB NOT NULL,
    projected_evidence_count INTEGER NOT NULL CHECK (projected_evidence_count >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS rag_retrieval_trace_operation_idx
    ON rag_retrieval_trace(operation_id, phase, call_index);
