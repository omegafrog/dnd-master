ALTER TABLE combat_map_movement_operation
    ADD COLUMN IF NOT EXISTS retry_resume_status TEXT NOT NULL DEFAULT 'PREPARING';
