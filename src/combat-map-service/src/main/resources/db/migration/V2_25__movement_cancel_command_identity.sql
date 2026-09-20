ALTER TABLE combat_map_movement_operation
    ADD COLUMN IF NOT EXISTS cancel_command_id UUID;

CREATE UNIQUE INDEX IF NOT EXISTS combat_map_movement_operation_cancel_command_uq
    ON combat_map_movement_operation(cancel_command_id)
    WHERE cancel_command_id IS NOT NULL;
