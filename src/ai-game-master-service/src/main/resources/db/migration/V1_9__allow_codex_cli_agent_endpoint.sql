ALTER TABLE agent_endpoint DROP CONSTRAINT IF EXISTS agent_endpoint_provider_check;
ALTER TABLE agent_endpoint
    ADD CONSTRAINT agent_endpoint_provider_check
    CHECK (provider IN ('OLLAMA', 'OPENAI_COMPATIBLE', 'CODEX_CLI'));
