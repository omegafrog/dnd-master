ALTER TABLE adventure_story_plan ADD COLUMN IF NOT EXISTS ending_count INTEGER NOT NULL DEFAULT 2 CHECK (ending_count BETWEEN 1 AND 4);
ALTER TABLE adventure_story_plan ADD COLUMN IF NOT EXISTS adventure_length TEXT NOT NULL DEFAULT 'STANDARD' CHECK (adventure_length IN ('SHORT', 'STANDARD', 'LONG'));
ALTER TABLE adventure_story_plan_history ADD COLUMN IF NOT EXISTS ending_count INTEGER NOT NULL DEFAULT 2 CHECK (ending_count BETWEEN 1 AND 4);
ALTER TABLE adventure_story_plan_history ADD COLUMN IF NOT EXISTS adventure_length TEXT NOT NULL DEFAULT 'STANDARD' CHECK (adventure_length IN ('SHORT', 'STANDARD', 'LONG'));
