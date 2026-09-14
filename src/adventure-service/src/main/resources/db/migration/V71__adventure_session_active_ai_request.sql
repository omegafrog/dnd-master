ALTER TABLE adventure_session
    ADD COLUMN IF NOT EXISTS active_ai_request_id UUID;
