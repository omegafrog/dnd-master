CREATE TABLE IF NOT EXISTS tactical_scene_preparation_job (
    job_id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    owner_player_id UUID NOT NULL,
    stage_position INTEGER NOT NULL,
    stage_name TEXT NOT NULL,
    status TEXT NOT NULL,
    progress INTEGER NOT NULL DEFAULT 0,
    attempts INTEGER NOT NULL DEFAULT 0,
    map_required BOOLEAN NOT NULL,
    message TEXT,
    failure_reason TEXT,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT tactical_scene_preparation_job_session_stage_key UNIQUE (session_id, stage_position)
);
CREATE INDEX IF NOT EXISTS tactical_scene_preparation_job_unfinished_idx
    ON tactical_scene_preparation_job(status);
