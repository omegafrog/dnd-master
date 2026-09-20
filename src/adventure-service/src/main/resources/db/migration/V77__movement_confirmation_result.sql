ALTER TABLE adventure_pending_map_movement_confirmation
    ADD COLUMN IF NOT EXISTS movement_result_json JSONB;

CREATE INDEX IF NOT EXISTS adventure_pending_map_movement_confirmation_turn_idx
    ON adventure_pending_map_movement_confirmation(pending_turn_id);
