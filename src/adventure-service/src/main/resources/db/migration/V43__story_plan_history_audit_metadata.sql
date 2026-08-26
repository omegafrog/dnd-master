ALTER TABLE adventure_story_plan_history ADD COLUMN IF NOT EXISTS cause TEXT NOT NULL DEFAULT 'LEGACY';
ALTER TABLE adventure_story_plan_history ADD COLUMN IF NOT EXISTS predecessor_history_id UUID;
