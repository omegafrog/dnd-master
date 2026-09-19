ALTER TABLE adventure_pending_map_movement_confirmation
    ADD COLUMN IF NOT EXISTS source_text TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS destination_x INTEGER,
    ADD COLUMN IF NOT EXISTS destination_y INTEGER,
    ADD COLUMN IF NOT EXISTS pending_turn_id UUID;
