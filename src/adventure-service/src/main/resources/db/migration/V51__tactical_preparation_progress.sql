ALTER TABLE tactical_scene_preparation_job ADD COLUMN IF NOT EXISTS progress_phase VARCHAR(80);
ALTER TABLE tactical_scene_preparation_job ADD COLUMN IF NOT EXISTS completed_units INTEGER;
ALTER TABLE tactical_scene_preparation_job ADD COLUMN IF NOT EXISTS total_units INTEGER;
UPDATE tactical_scene_preparation_job
SET progress_phase = COALESCE(progress_phase, 'LEGACY'),
    completed_units = COALESCE(completed_units, progress),
    total_units = COALESCE(total_units, 100)
WHERE progress_phase IS NULL OR completed_units IS NULL OR total_units IS NULL;
