ALTER TABLE combat_map_movement_operation
    ADD COLUMN IF NOT EXISTS result_hostile_token_id UUID;
