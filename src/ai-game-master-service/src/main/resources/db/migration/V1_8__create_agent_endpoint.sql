CREATE TABLE IF NOT EXISTS agent_endpoint (
    endpoint_id UUID PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    provider TEXT NOT NULL CHECK (provider IN ('OLLAMA', 'OPENAI_COMPATIBLE')),
    base_url TEXT NOT NULL,
    model TEXT NOT NULL,
    secret_environment_variable TEXT,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS agent_endpoint_one_active_idx ON agent_endpoint ((active)) WHERE active = TRUE;
