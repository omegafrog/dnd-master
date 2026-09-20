ALTER TABLE combat_map_movement_operation
    ADD COLUMN IF NOT EXISTS hostile_observations TEXT NOT NULL DEFAULT '';
