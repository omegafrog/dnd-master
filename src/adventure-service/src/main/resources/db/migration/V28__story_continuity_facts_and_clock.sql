CREATE TABLE IF NOT EXISTS adventure_clock (
    session_id UUID PRIMARY KEY REFERENCES adventure_session(session_id) ON DELETE CASCADE,
    clock_version BIGINT NOT NULL CHECK (clock_version >= 0),
    turns_elapsed BIGINT NOT NULL CHECK (turns_elapsed >= 0),
    seconds_elapsed BIGINT NOT NULL CHECK (seconds_elapsed >= 0),
    last_cause_turn_id UUID,
    rule_reference TEXT,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS committed_world_fact (
    fact_id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES adventure_session(session_id) ON DELETE CASCADE,
    fact_version BIGINT NOT NULL CHECK (fact_version > 0),
    subject TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object_value TEXT NOT NULL,
    visibility TEXT NOT NULL CHECK (visibility IN ('PUBLIC', 'GM_ONLY')),
    provenance TEXT NOT NULL,
    cause_turn_id UUID NOT NULL,
    committed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (session_id, fact_version)
);
CREATE INDEX IF NOT EXISTS committed_world_fact_session_idx ON committed_world_fact(session_id, fact_version);

CREATE TABLE IF NOT EXISTS adventure_story_plan_revision (
    revision_id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES adventure_session(session_id) ON DELETE CASCADE,
    plan_version BIGINT NOT NULL CHECK (plan_version > 0),
    predecessor_revision_id UUID,
    cause_turn_id UUID NOT NULL,
    stages_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (session_id, plan_version)
);
CREATE TABLE IF NOT EXISTS adventure_story_plan_current (
    session_id UUID PRIMARY KEY REFERENCES adventure_session(session_id) ON DELETE CASCADE,
    revision_id UUID NOT NULL REFERENCES adventure_story_plan_revision(revision_id),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
