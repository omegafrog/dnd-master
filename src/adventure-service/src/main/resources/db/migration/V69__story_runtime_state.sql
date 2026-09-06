ALTER TABLE adventure
    ADD COLUMN IF NOT EXISTS story_runtime_state_jsonb JSONB;

COMMENT ON COLUMN adventure.story_runtime_state_jsonb IS
    'Canonical Story Runtime state: current stage, Situation pool, Revelations, and Pressure';
