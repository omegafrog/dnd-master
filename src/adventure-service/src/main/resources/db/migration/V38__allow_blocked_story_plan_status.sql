ALTER TABLE adventure_story_plan
    DROP CONSTRAINT IF EXISTS adventure_story_plan_status_check;

ALTER TABLE adventure_story_plan
    ADD CONSTRAINT adventure_story_plan_status_check
    CHECK (status IN ('GENERATING', 'READY', 'BLOCKED', 'FAILED'));

ALTER TABLE adventure_story_plan_history
    DROP CONSTRAINT IF EXISTS adventure_story_plan_history_status_check;

ALTER TABLE adventure_story_plan_history
    ADD CONSTRAINT adventure_story_plan_history_status_check
    CHECK (status IN ('GENERATING', 'READY', 'BLOCKED', 'FAILED'));
