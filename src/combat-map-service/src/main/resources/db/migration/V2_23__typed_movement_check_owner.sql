ALTER TABLE combat_map_movement_operation
    ADD COLUMN IF NOT EXISTS pending_owner_actor TEXT;

ALTER TABLE combat_map_movement_operation
    ADD COLUMN IF NOT EXISTS pending_owner_player_id UUID;

UPDATE combat_map_movement_operation
SET pending_owner_actor = pending_ownership
WHERE pending_check_id IS NOT NULL AND pending_owner_actor IS NULL;

UPDATE combat_map_movement_operation
SET pending_owner_player_id = player_id
WHERE pending_check_id IS NOT NULL AND pending_owner_player_id IS NULL;
