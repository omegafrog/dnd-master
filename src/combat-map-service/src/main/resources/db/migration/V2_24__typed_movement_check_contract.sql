ALTER TABLE combat_map_movement_operation
    ADD COLUMN IF NOT EXISTS pending_dice_expression TEXT,
    ADD COLUMN IF NOT EXISTS pending_modifier INTEGER NOT NULL DEFAULT 0;

ALTER TABLE combat_map_spatial_feature
    ADD COLUMN IF NOT EXISTS detection_dice_expression TEXT,
    ADD COLUMN IF NOT EXISTS detection_modifier INTEGER NOT NULL DEFAULT 0;

ALTER TABLE combat_map_command_spatial_feature_history
    ADD COLUMN IF NOT EXISTS detection_dice_expression TEXT,
    ADD COLUMN IF NOT EXISTS detection_modifier INTEGER NOT NULL DEFAULT 0;

UPDATE combat_map_movement_operation
SET pending_dice_expression = '1d20'
WHERE pending_check_id IS NOT NULL AND (pending_dice_expression IS NULL OR pending_dice_expression = '');
