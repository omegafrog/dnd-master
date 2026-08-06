CREATE TABLE IF NOT EXISTS adventure_story_plan (
    plan_id UUID PRIMARY KEY,
    session_id UUID NOT NULL UNIQUE REFERENCES adventure_session(session_id) ON DELETE CASCADE,
    package_revision BIGINT NOT NULL CHECK (package_revision > 0),
    party_revision BIGINT NOT NULL CHECK (party_revision >= 0),
    plan_version BIGINT NOT NULL CHECK (plan_version > 0),
    status TEXT NOT NULL CHECK (status IN ('GENERATING', 'READY', 'FAILED')),
    current_stage INTEGER NOT NULL CHECK (current_stage >= 0),
    stages_json TEXT NOT NULL,
    failure_reason TEXT,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
