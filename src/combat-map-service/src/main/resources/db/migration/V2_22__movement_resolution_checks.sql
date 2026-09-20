ALTER TABLE combat_map_movement_operation
    ADD COLUMN IF NOT EXISTS pending_check_id UUID,
    ADD COLUMN IF NOT EXISTS pending_feature_id UUID,
    ADD COLUMN IF NOT EXISTS pending_feature_type TEXT,
    ADD COLUMN IF NOT EXISTS pending_trigger TEXT,
    ADD COLUMN IF NOT EXISTS pending_rule_reference TEXT,
    ADD COLUMN IF NOT EXISTS pending_difficulty INTEGER,
    ADD COLUMN IF NOT EXISTS pending_mode TEXT,
    ADD COLUMN IF NOT EXISTS pending_ownership TEXT,
    ADD COLUMN IF NOT EXISTS check_outcomes TEXT NOT NULL DEFAULT '';

DROP INDEX IF EXISTS combat_map_movement_operation_active_map_uq;
CREATE UNIQUE INDEX IF NOT EXISTS combat_map_movement_operation_active_map_uq
    ON combat_map_movement_operation(map_id)
    WHERE status IN ('PREPARING', 'CHECK_PENDING', 'RETRY_WAIT', 'READY_TO_COMMIT');
