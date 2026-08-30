ALTER TABLE tactical_scene_preparation_job ADD COLUMN IF NOT EXISTS lease_token UUID;
ALTER TABLE tactical_scene_preparation_job ADD COLUMN IF NOT EXISTS lease_until TIMESTAMPTZ;
CREATE INDEX IF NOT EXISTS tactical_scene_preparation_job_lease_idx
    ON tactical_scene_preparation_job(status, lease_until);
