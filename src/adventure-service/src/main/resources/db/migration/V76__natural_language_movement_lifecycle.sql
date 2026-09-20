ALTER TABLE adventure_pending_map_movement_confirmation
    ADD COLUMN IF NOT EXISTS confirmation_command_id UUID,
    ADD COLUMN IF NOT EXISTS terminal BOOLEAN NOT NULL DEFAULT FALSE;
