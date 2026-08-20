DELETE FROM adventure_story_plan_history duplicate
USING adventure_story_plan_history keeper
WHERE duplicate.session_id = keeper.session_id
  AND duplicate.plan_version = keeper.plan_version
  AND (duplicate.recorded_at < keeper.recorded_at
       OR (duplicate.recorded_at = keeper.recorded_at AND duplicate.history_id::text < keeper.history_id::text));

CREATE UNIQUE INDEX IF NOT EXISTS adventure_story_plan_history_session_version_idx
    ON adventure_story_plan_history(session_id, plan_version);
