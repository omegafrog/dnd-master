CREATE UNIQUE INDEX IF NOT EXISTS adventure_story_plan_history_session_version_idx
    ON adventure_story_plan_history(session_id, plan_version);
